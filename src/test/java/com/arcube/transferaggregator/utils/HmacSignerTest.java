package com.arcube.transferaggregator.utils;

import com.arcube.transferaggregator.config.TokenProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacSignerTest {

    private static TokenProperties tokenProps(String secret) {
        TokenProperties props = new TokenProperties();
        props.setSecret(secret);
        props.validate();
        return props;
    }

    @Test
    void signIsDeterministicForSameSecret() {
        HmacSigner signer = new HmacSigner(tokenProps("01234567890123456789012345678901"));
        String sig1 = signer.sign("payload");
        String sig2 = signer.sign("payload");
        assertThat(sig1).isEqualTo(sig2);
    }

    @Test
    void signChangesWithDifferentSecrets() {
        HmacSigner signerA = new HmacSigner(tokenProps("01234567890123456789012345678901"));
        HmacSigner signerB = new HmacSigner(tokenProps("abcdefghijklmnopqrstuvwxyzABCDEF0123456789"));
        assertThat(signerA.sign("payload")).isNotEqualTo(signerB.sign("payload"));
    }

    @Test
    void encodeDecodeRoundTrip() {
        HmacSigner signer = new HmacSigner(tokenProps("01234567890123456789012345678901"));
        String encoded = signer.encode("hello-world");
        assertThat(signer.decode(encoded)).isEqualTo("hello-world");
    }

    @Test
    void signWrapsCryptoErrors() {
        HmacSigner signer = new HmacSigner(tokenProps("01234567890123456789012345678901")) {
            @Override
            protected Mac createMac() throws NoSuchAlgorithmException {
                throw new NoSuchAlgorithmException("boom");
            }
        };

        assertThatThrownBy(() -> signer.sign("payload"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Failed to sign data");
    }
}
