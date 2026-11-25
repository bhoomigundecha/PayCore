package com.bhoomi.paycore.webhook;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signs webhook bodies so merchants can tell a real PayCore call from a forged one.
 *
 * <p>Header: {@code PayCore-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, t + "." + body)>}.
 * The timestamp is inside the signed text, so an attacker who captured one request can't replay it
 * later: receivers reject anything older than {@link #TOLERANCE_SECONDS}.
 */
public final class WebhookSigner {

    public static final String HEADER = "PayCore-Signature";
    public static final long TOLERANCE_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSigner() {
    }

    public static String newSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String header(String secret, long timestamp, String body) {
        return "t=" + timestamp + ",v1=" + hmac(secret, timestamp + "." + body);
    }

    /** What every receiver should do: recompute the HMAC, compare in constant time, check the age. */
    public static boolean verify(String secret, String header, String body, long nowSeconds) {
        if (header == null) {
            return false;
        }
        Long t = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("t")) {
                try {
                    t = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (kv[0].equals("v1")) {
                v1 = kv[1];
            }
        }
        if (t == null || v1 == null || Math.abs(nowSeconds - t) > TOLERANCE_SECONDS) {
            return false;
        }
        byte[] expected = hmac(secret, t + "." + body).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, v1.getBytes(StandardCharsets.US_ASCII));
    }

    private static String hmac(String secret, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
