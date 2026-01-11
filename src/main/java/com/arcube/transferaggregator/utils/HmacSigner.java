package com.arcube.transferaggregator.utils;

import com.arcube.transferaggregator.config.TokenProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Shared HMAC-SHA256 signing utility for token generation */
@Component
@RequiredArgsConstructor
public class HmacSigner {

    private static final String HMAC_ALG = "HmacSHA256";
    public static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    public static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final TokenProperties tokenProperties;

    /** Signs the given data using HMAC-SHA256 and returns a URL-safe Base64 encoded signature */
    public String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(
                tokenProperties.getSecret().getBytes(StandardCharsets.UTF_8), 
                HMAC_ALG
            ));
            return URL_ENCODER.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to sign data", e);
        }
    }

    /** Encodes a string to URL-safe Base64 */
    public String encode(String data) {
        return URL_ENCODER.encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a URL-safe Base64 string */
    public String decode(String encoded) {
        return new String(URL_DECODER.decode(encoded), StandardCharsets.UTF_8);
    }
}
