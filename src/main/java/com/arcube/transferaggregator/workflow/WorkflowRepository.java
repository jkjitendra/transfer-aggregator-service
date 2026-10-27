package com.arcube.transferaggregator.workflow;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL source of truth for write operations and idempotency claims. */
@Repository
@Profile("!demo & !test")
public class WorkflowRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EnvelopeCrypto crypto;
    private final int idempotencyDays;
    private final int operationDays;

    public WorkflowRepository(JdbcTemplate jdbc, TransactionTemplate transactions, EnvelopeCrypto crypto,
                              @Value("${workflow.idempotency-retention-days:7}") int idempotencyDays,
                              @Value("${workflow.operation-retention-days:90}") int operationDays) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.crypto = crypto;
        if (idempotencyDays < 1 || operationDays < idempotencyDays) {
            throw new IllegalStateException("Workflow retention must keep operations at least as long as idempotency records");
        }
        this.idempotencyDays = idempotencyDays;
        this.operationDays = operationDays;
    }

    public record Operation(UUID id, String tenantId, String kind, String state, String supplierCode,
                            String fingerprint, byte[] encryptedResponse, Instant createdAt, Instant updatedAt) {}
    public record Claim(Operation operation, boolean acquired) {}

    public Claim claim(String tenantId, String kind, String clientKey, String fingerprint, String supplierCode) {
        if (clientKey == null || clientKey.isBlank() || clientKey.length() > 256) {
            throw new IllegalArgumentException("Idempotency-Key is required and must be at most 256 characters");
        }
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid request fingerprint");
        }
        String keyHash = sha256(clientKey);
        return transactions.execute(ignored -> {
            UUID candidate = UUID.randomUUID();
            Instant expiry = Instant.now().plus(idempotencyDays, ChronoUnit.DAYS);
            jdbc.update("INSERT INTO operations (id,tenant_id,kind,state,supplier_code,request_fingerprint,expires_at) " +
                "VALUES (?,?,?,'IN_PROGRESS',?,?,?)", candidate, tenantId, kind, supplierCode,
                fingerprint, Timestamp.from(Instant.now().plus(operationDays, ChronoUnit.DAYS)));
            int claimed = jdbc.update("INSERT INTO idempotency_records " +
                    "(tenant_id,operation_kind,key_hash,request_fingerprint,operation_id,expires_at) VALUES (?,?,?,?,?,?) " +
                    "ON CONFLICT (tenant_id,operation_kind,key_hash) DO UPDATE SET " +
                    "request_fingerprint=EXCLUDED.request_fingerprint, operation_id=EXCLUDED.operation_id, " +
                    "expires_at=EXCLUDED.expires_at WHERE idempotency_records.expires_at <= now()",
                tenantId, kind, keyHash, fingerprint, candidate, Timestamp.from(expiry));
            if (claimed == 1) {
                audit(candidate, tenantId, "system", "CLAIMED", kind);
                return new Claim(get(candidate).orElseThrow(), true);
            }
            jdbc.update("DELETE FROM operations WHERE id=?", candidate);
            Operation existing = jdbc.queryForObject("SELECT o.* FROM operations o JOIN idempotency_records i " +
                    "ON i.operation_id=o.id WHERE i.tenant_id=? AND i.operation_kind=? AND i.key_hash=?",
                this::mapOperation, tenantId, kind, keyHash);
            if (existing == null || !fingerprint.equals(existing.fingerprint())) {
                throw new IdempotencyConflictException();
            }
            return new Claim(existing, false);
        });
    }

    public Optional<Operation> get(UUID id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM operations WHERE id=?",
                this::mapOperation, id));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public Optional<Operation> getForTenant(UUID id, String tenantId) {
        return get(id).filter(operation -> operation.tenantId().equals(tenantId));
    }

    public List<Operation> reviewQueue(int limit) {
        return jdbc.query("SELECT * FROM operations WHERE state IN ('UNKNOWN_OUTCOME','MANUAL_REVIEW') " +
            "ORDER BY updated_at LIMIT ?", this::mapOperation, Math.min(Math.max(limit, 1), 100));
    }

    public void complete(UUID id, String tenantId, String state, String responseJson) {
        byte[] encrypted = responseJson == null ? null : crypto.encrypt(responseJson);
        transactions.executeWithoutResult(ignored -> {
            int changed = jdbc.update("UPDATE operations SET state=?, response_ciphertext=?, updated_at=now() " +
                    "WHERE id=? AND tenant_id=? AND state IN ('IN_PROGRESS','UNKNOWN_OUTCOME')",
                state, encrypted, id, tenantId);
            if (changed == 1) audit(id, tenantId, "system", "STATE_CHANGED", state);
        });
    }

    public String responseJson(Operation operation) {
        return operation.encryptedResponse() == null ? null : crypto.decrypt(operation.encryptedResponse());
    }

    public void markStaleWrites() {
        transactions.executeWithoutResult(ignored -> {
            List<Operation> stale = jdbc.query("UPDATE operations SET state='UNKNOWN_OUTCOME', updated_at=now() " +
                    "WHERE kind IN ('BOOKING','CHANGE') AND state='IN_PROGRESS' " +
                    "AND updated_at < now() - interval '2 minutes' RETURNING *", this::mapOperation);
            stale.forEach(operation -> audit(operation.id(), operation.tenantId(), "system", "UNKNOWN_OUTCOME", "STALE_WRITE"));
        });
    }

    public boolean review(UUID id, String state, String actor, String noteCode) {
        if (!List.of("FAILED", "MANUAL_REVIEW").contains(state)) {
            throw new IllegalArgumentException("Unsupported review state");
        }
        return transactions.execute(ignored -> {
            Operation operation = get(id).orElseThrow();
            int changed = jdbc.update("UPDATE operations SET state=?, updated_at=now() WHERE id=? " +
                "AND state IN ('UNKNOWN_OUTCOME','MANUAL_REVIEW')", state, id);
            if (changed == 1) audit(id, operation.tenantId(), actor, "REVIEWED", noteCode);
            return changed == 1;
        });
    }

    public void cleanup() {
        transactions.executeWithoutResult(ignored -> {
            jdbc.update("DELETE FROM idempotency_records WHERE expires_at < now()");
            jdbc.update("DELETE FROM audit_events WHERE created_at < now() - (? * interval '1 day')", operationDays);
            jdbc.update("DELETE FROM dead_letters WHERE resolved_at < now() - (? * interval '1 day')", operationDays);
            jdbc.update("DELETE FROM cancellation_jobs WHERE state IN ('SUCCEEDED','RESOLVED') " +
                "AND updated_at < now() - (? * interval '1 day')", operationDays);
            jdbc.update("DELETE FROM operations o WHERE state IN ('CONFIRMED','FAILED','SUCCEEDED','RESOLVED') " +
                "AND updated_at < now() - (? * interval '1 day') AND NOT EXISTS " +
                "(SELECT 1 FROM idempotency_records i WHERE i.operation_id=o.id) AND NOT EXISTS " +
                "(SELECT 1 FROM cancellation_jobs j WHERE j.operation_id=o.id) AND NOT EXISTS " +
                "(SELECT 1 FROM audit_events a WHERE a.operation_id=o.id)", operationDays);
        });
    }

    public void audit(UUID operationId, String tenantId, String actor, String action, String detailCode) {
        String actorReference = actor == null || "system".equals(actor) ? "system" : sha256(actor);
        jdbc.update("INSERT INTO audit_events (operation_id,tenant_id,actor,action,detail_code) VALUES (?,?,?,?,?)",
            operationId, tenantId, actorReference, action, detailCode);
    }

    private Operation mapOperation(ResultSet rs, int row) throws SQLException {
        return new Operation(rs.getObject("id", UUID.class), rs.getString("tenant_id"), rs.getString("kind"),
            rs.getString("state"), rs.getString("supplier_code"), rs.getString("request_fingerprint"),
            rs.getBytes("response_ciphertext"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
