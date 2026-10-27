package com.arcube.transferaggregator.workflow;

import com.arcube.transferaggregator.dto.CancelResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable cancellation queue, lease state, and dead-letter source of truth. */
@Repository
@Profile("!demo & !test")
public class CancellationRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EnvelopeCrypto crypto;
    private final WorkflowRepository workflows;
    private final ObjectMapper mapper;
    private final int leaseSeconds;
    private final int operationDays;

    public CancellationRepository(JdbcTemplate jdbc, TransactionTemplate transactions, EnvelopeCrypto crypto,
                                  WorkflowRepository workflows, ObjectMapper mapper,
                                  @Value("${workflow.cancellation-lease-seconds:30}") int leaseSeconds,
                                  @Value("${workflow.operation-retention-days:90}") int operationDays) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.crypto = crypto;
        this.workflows = workflows;
        this.mapper = mapper;
        if (leaseSeconds < 10 || leaseSeconds > 3600) throw new IllegalStateException("Invalid cancellation lease");
        this.leaseSeconds = leaseSeconds;
        this.operationDays = operationDays;
    }

    public record Job(UUID id, UUID operationId, String tenantId, String bookingHash,
                      String supplierCode, byte[] reservationCiphertext, String state, int attempts,
                      UUID leaseOwner, Instant leaseUntil, String lastErrorCode, Instant createdAt) {}
    public record DeadLetter(UUID jobId, UUID operationId, String tenantId, String supplierCode,
                             int attempts, String reasonCode, Instant createdAt, int replayCount) {}

    public Job request(String tenant, String bookingId, String supplier, String reservationId) {
        String bookingHash = WorkflowRepository.sha256(bookingId);
        return transactions.execute(ignored -> {
            UUID operationId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            jdbc.update("INSERT INTO operations (id,tenant_id,kind,state,supplier_code,expires_at) " +
                    "VALUES (?,?,'CANCELLATION','REQUESTED',?,now() + (? * interval '1 day'))",
                operationId, tenant, supplier, operationDays);
            int inserted = jdbc.update("INSERT INTO cancellation_jobs (id,operation_id,tenant_id,booking_hash," +
                    "supplier_code,reservation_ciphertext,state) VALUES (?,?,?,?,?,?,'REQUESTED') " +
                    "ON CONFLICT (tenant_id,booking_hash) DO NOTHING",
                jobId, operationId, tenant, bookingHash, supplier, crypto.encrypt(reservationId));
            if (inserted == 0) {
                jdbc.update("DELETE FROM operations WHERE id=?", operationId);
                return find(tenant, bookingHash).orElseThrow();
            }
            workflows.audit(operationId, tenant, "system", "CANCELLATION_REQUESTED", supplier);
            return find(tenant, bookingHash).orElseThrow();
        });
    }

    public Optional<Job> find(String tenant, String bookingHash) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM cancellation_jobs " +
                "WHERE tenant_id=? AND booking_hash=?", this::mapJob, tenant, bookingHash));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public Optional<Job> find(UUID jobId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("SELECT * FROM cancellation_jobs WHERE id=?",
                this::mapJob, jobId));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    public List<Job> claimDue(int limit) {
        UUID owner = UUID.randomUUID();
        return transactions.execute(ignored -> {
            List<Job> claimed = jdbc.query("WITH due AS (SELECT id FROM cancellation_jobs WHERE state IN " +
                    "('REQUESTED','RETRY_SCHEDULED') AND next_attempt_at <= now() " +
                    "ORDER BY next_attempt_at,created_at LIMIT ? FOR UPDATE SKIP LOCKED) " +
                    "UPDATE cancellation_jobs j SET state='IN_PROGRESS', attempt_count=attempt_count+1, " +
                    "lease_owner=?, lease_until=now() + (? * interval '1 second'), updated_at=now() " +
                    "FROM due WHERE j.id=due.id RETURNING j.*",
                this::mapJob, Math.min(Math.max(limit, 1), 50), owner, leaseSeconds);
            for (Job job : claimed) {
                jdbc.update("UPDATE operations SET state='IN_PROGRESS',updated_at=now() WHERE id=?", job.operationId());
                workflows.audit(job.operationId(), job.tenantId(), "system", "CANCELLATION_CLAIMED", job.supplierCode());
            }
            return claimed;
        });
    }

    public List<Job> expiredLeases() {
        return jdbc.query("SELECT * FROM cancellation_jobs WHERE state='IN_PROGRESS' " +
            "AND lease_until < now() ORDER BY lease_until LIMIT 50", this::mapJob);
    }

    public void recover(Job job, boolean safeToRetry, int maxAttempts) {
        transactions.executeWithoutResult(ignored -> {
            String state = !safeToRetry ? "UNKNOWN_OUTCOME" :
                (job.attempts() >= maxAttempts ? "DLQ" : "RETRY_SCHEDULED");
            long delaySeconds = Math.min(300, 1L << Math.min(8, job.attempts()));
            delaySeconds += java.util.concurrent.ThreadLocalRandom.current().nextLong(Math.max(1, delaySeconds));
            Instant next = Instant.now().plusSeconds(delaySeconds);
            int changed = jdbc.update("UPDATE cancellation_jobs SET state=?, lease_owner=NULL, lease_until=NULL, " +
                    "next_attempt_at=?, last_error_code='LEASE_EXPIRED', updated_at=now() " +
                    "WHERE id=? AND state='IN_PROGRESS' AND lease_until < now()",
                state, Timestamp.from(next), job.id());
            if (changed == 1) {
                jdbc.update("UPDATE operations SET state=?,updated_at=now() WHERE id=?", state, job.operationId());
                if ("DLQ".equals(state)) {
                    jdbc.update("INSERT INTO dead_letters (job_id,reason_code) VALUES (?,'LEASE_EXPIRED') " +
                        "ON CONFLICT (job_id) DO UPDATE SET reason_code='LEASE_EXPIRED',created_at=now(),resolved_at=NULL",
                        job.id());
                }
                workflows.audit(job.operationId(), job.tenantId(), "system", "LEASE_RECOVERED", state);
            }
        });
    }

    public void succeeded(Job job, CancelResponse response) {
        transactions.executeWithoutResult(ignored -> {
            int changed = jdbc.update("UPDATE cancellation_jobs SET state='SUCCEEDED', lease_owner=NULL, " +
                    "lease_until=NULL, updated_at=now() WHERE id=? AND lease_owner=? AND state='IN_PROGRESS'",
                job.id(), job.leaseOwner());
            if (changed == 1) {
                jdbc.update("UPDATE operations SET state='SUCCEEDED',response_ciphertext=?,updated_at=now() WHERE id=?",
                    crypto.encrypt(write(response)), job.operationId());
                workflows.audit(job.operationId(), job.tenantId(), "system", "CANCELLATION_SUCCEEDED", job.supplierCode());
            }
        });
    }

    public void failedAttempt(Job job, boolean safeToRetry, boolean permanent, int maxAttempts, String reasonCode) {
        String state = !safeToRetry ? "UNKNOWN_OUTCOME" :
            (permanent || job.attempts() >= maxAttempts ? "DLQ" : "RETRY_SCHEDULED");
        long delaySeconds = Math.min(300, 1L << Math.min(8, Math.max(0, job.attempts() - 1)));
        delaySeconds += java.util.concurrent.ThreadLocalRandom.current().nextLong(Math.max(1, delaySeconds));
        Instant next = Instant.now().plusSeconds(delaySeconds);
        transactions.executeWithoutResult(ignored -> {
            int changed = jdbc.update("UPDATE cancellation_jobs SET state=?, lease_owner=NULL, lease_until=NULL, " +
                    "next_attempt_at=?, last_error_code=?, updated_at=now() WHERE id=? " +
                    "AND lease_owner=? AND state='IN_PROGRESS'",
                state, Timestamp.from(next), reasonCode, job.id(), job.leaseOwner());
            if (changed == 0) return;
            jdbc.update("UPDATE operations SET state=?,updated_at=now() WHERE id=?", state, job.operationId());
            if ("DLQ".equals(state)) {
                jdbc.update("INSERT INTO dead_letters (job_id,reason_code) VALUES (?,?) " +
                    "ON CONFLICT (job_id) DO UPDATE SET reason_code=EXCLUDED.reason_code, " +
                    "created_at=now(),resolved_at=NULL,resolved_by=NULL,resolution_note=NULL", job.id(), reasonCode);
            }
            workflows.audit(job.operationId(), job.tenantId(), "system", "CANCELLATION_ATTEMPT", state);
        });
    }

    public List<DeadLetter> deadLetters(int limit) {
        return jdbc.query("SELECT d.job_id,j.operation_id,j.tenant_id,j.supplier_code,j.attempt_count," +
                "d.reason_code,d.created_at,d.replay_count FROM dead_letters d JOIN cancellation_jobs j " +
                "ON j.id=d.job_id WHERE j.state='DLQ' AND d.resolved_at IS NULL " +
                "ORDER BY d.created_at LIMIT ?", (rs, row) -> new DeadLetter(
                rs.getObject("job_id", UUID.class), rs.getObject("operation_id", UUID.class),
                rs.getString("tenant_id"), rs.getString("supplier_code"), rs.getInt("attempt_count"),
                rs.getString("reason_code"), rs.getTimestamp("created_at").toInstant(), rs.getInt("replay_count")),
            Math.min(Math.max(limit, 1), 100));
    }

    public int activeDeadLetterCount() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM dead_letters d JOIN cancellation_jobs j " +
            "ON j.id=d.job_id WHERE j.state='DLQ' AND d.resolved_at IS NULL", Integer.class);
        return count == null ? 0 : count;
    }

    public boolean replay(UUID jobId, String actor, String evidenceReference) {
        return transactions.execute(ignored -> {
            Job job = find(jobId).orElseThrow();
            int changed = jdbc.update("UPDATE cancellation_jobs SET state='RETRY_SCHEDULED', attempt_count=0," +
                "next_attempt_at=now(),updated_at=now() WHERE id=? AND state='DLQ'", jobId);
            if (changed == 0) return false;
            jdbc.update("UPDATE dead_letters SET resolved_at=now(),resolved_by=?,resolution_note=?," +
                "replay_count=replay_count+1 WHERE job_id=?", WorkflowRepository.sha256(actor), evidenceReference, jobId);
            jdbc.update("UPDATE operations SET state='RETRY_SCHEDULED',updated_at=now() WHERE id=?", job.operationId());
            workflows.audit(job.operationId(), job.tenantId(), actor, "DLQ_REPLAY", "EVIDENCE_RECORDED");
            return true;
        });
    }

    public boolean resolve(UUID jobId, String actor, String evidenceReference) {
        return transactions.execute(ignored -> {
            Job job = find(jobId).orElseThrow();
            int changed = jdbc.update("UPDATE cancellation_jobs SET state='RESOLVED',updated_at=now() " +
                "WHERE id=? AND state IN ('DLQ','UNKNOWN_OUTCOME')", jobId);
            if (changed == 0) return false;
            jdbc.update("UPDATE dead_letters SET resolved_at=now(),resolved_by=?,resolution_note=? WHERE job_id=?",
                WorkflowRepository.sha256(actor), evidenceReference, jobId);
            jdbc.update("UPDATE operations SET state='RESOLVED',updated_at=now() WHERE id=?", job.operationId());
            workflows.audit(job.operationId(), job.tenantId(), actor, "CANCELLATION_RESOLVED", "EVIDENCE_RECORDED");
            return true;
        });
    }

    public String reservationId(Job job) {
        return crypto.decrypt(job.reservationCiphertext());
    }

    public Optional<CancelResponse> storedResponse(Job job) {
        return workflows.get(job.operationId()).map(WorkflowRepository.Operation::encryptedResponse)
            .filter(bytes -> bytes != null).map(crypto::decrypt).map(json -> {
                try {
                    return mapper.readValue(json, CancelResponse.class);
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException("Stored cancellation response is invalid", exception);
                }
            });
    }

    private String write(CancelResponse response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize cancellation response", exception);
        }
    }

    private Job mapJob(ResultSet rs, int row) throws SQLException {
        Timestamp lease = rs.getTimestamp("lease_until");
        return new Job(rs.getObject("id", UUID.class), rs.getObject("operation_id", UUID.class),
            rs.getString("tenant_id"), rs.getString("booking_hash"), rs.getString("supplier_code"),
            rs.getBytes("reservation_ciphertext"), rs.getString("state"), rs.getInt("attempt_count"),
            rs.getObject("lease_owner", UUID.class), lease == null ? null : lease.toInstant(),
            rs.getString("last_error_code"), rs.getTimestamp("created_at").toInstant());
    }
}
