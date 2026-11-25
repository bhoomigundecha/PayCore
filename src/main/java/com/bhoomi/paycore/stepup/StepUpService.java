package com.bhoomi.paycore.stepup;

import com.bhoomi.paycore.common.InvalidStateException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Step-up authentication for large payments: a one-time code the payer must enter before any money moves.
 *
 * <ul>
 *   <li><b>Dynamic linking.</b> Only an HMAC of (payment id, amount, payee, code) is stored, so a code can
 *       approve exactly one payment of exactly one amount to exactly one payee.</li>
 *   <li><b>Short-lived.</b> The challenge lives in Redis with a 5-minute TTL.</li>
 *   <li><b>Brute-force resistant.</b> 3 attempts (counted atomically before comparing), constant-time
 *       comparison, and a 30-second cooldown between resends.</li>
 * </ul>
 */
@Service
public class StepUpService {

    static final Duration TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(30);
    static final int MAX_ATTEMPTS = 3;

    private final StringRedisTemplate redis;
    private final PhoneInbox inbox;
    private final long threshold;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public StepUpService(StringRedisTemplate redis, PhoneInbox inbox,
                         @Value("${paycore.step-up.threshold}") long threshold,
                         @Value("${paycore.step-up.code-key}") String codeKey) {
        this.redis = redis;
        this.inbox = inbox;
        this.threshold = threshold;
        byte[] raw = Base64.getDecoder().decode(codeKey);
        if (raw.length != 32) {
            throw new IllegalArgumentException("paycore.step-up.code-key must be 32 bytes, base64-encoded");
        }
        this.key = new SecretKeySpec(raw, "HmacSHA256");
    }

    /** Payments at or above the threshold (in paise) need the payer's approval. */
    public boolean isRequired(long amount) {
        return amount >= threshold;
    }

    public long threshold() {
        return threshold;
    }

    /** Creates a fresh challenge for the payment and sends the code to the payer's phone. */
    public void issue(Challenge challenge) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        String redisKey = challengeKey(challenge.paymentId());
        redis.delete(redisKey);
        redis.opsForHash().putAll(redisKey, Map.of("hash", bind(challenge, code), "attempts", "0"));
        redis.expire(redisKey, TTL);
        redis.opsForValue().set(cooldownKey(challenge.paymentId()), "1", RESEND_COOLDOWN);
        inbox.deliver(challenge.payerUserId(), "PayCore: " + code + " is your code to approve " + challenge.amountText()
                + " to " + challenge.payeeName() + ". Valid for 5 minutes. Never share it.");
    }

    /** A new code, invalidating the old one. Refused during the cooldown. */
    public void resend(Challenge challenge) {
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey(challenge.paymentId())))) {
            throw new InvalidStateException("Please wait a few seconds before requesting another code");
        }
        issue(challenge);
    }

    public Verification verify(Challenge challenge, String code) {
        String redisKey = challengeKey(challenge.paymentId());
        Object stored = redis.opsForHash().get(redisKey, "hash");
        if (stored == null) {
            return Verification.expired();
        }
        // Count the attempt before comparing, so parallel guesses can't exceed the limit.
        long attempts = redis.opsForHash().increment(redisKey, "attempts", 1);
        if (attempts > MAX_ATTEMPTS) {
            redis.delete(redisKey);
            return Verification.locked();
        }
        boolean matches = code != null && code.matches("\\d{6}") && MessageDigest.isEqual(
                stored.toString().getBytes(StandardCharsets.US_ASCII),
                bind(challenge, code).getBytes(StandardCharsets.US_ASCII));
        if (matches) {
            redis.delete(redisKey);
            return Verification.approved();
        }
        int left = (int) (MAX_ATTEMPTS - attempts);
        if (left <= 0) {
            redis.delete(redisKey);
            return Verification.locked();
        }
        return Verification.wrong(left);
    }

    public void clear(UUID paymentId) {
        redis.delete(challengeKey(paymentId));
    }

    private String bind(Challenge c, String code) {
        String message = c.paymentId() + "|" + c.amount() + "|" + c.payeeAccountId() + "|" + code;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    private static String challengeKey(UUID paymentId) {
        return "stepup:" + paymentId;
    }

    private static String cooldownKey(UUID paymentId) {
        return "stepup:cooldown:" + paymentId;
    }

    /** What a code is bound to. */
    public record Challenge(UUID paymentId, long amount, UUID payeeAccountId, UUID payerUserId,
                            String amountText, String payeeName) {
    }

    public record Verification(Outcome outcome, int attemptsLeft) {
        public enum Outcome { APPROVED, WRONG, LOCKED, EXPIRED }

        static Verification approved() {
            return new Verification(Outcome.APPROVED, 0);
        }

        static Verification wrong(int left) {
            return new Verification(Outcome.WRONG, left);
        }

        static Verification locked() {
            return new Verification(Outcome.LOCKED, 0);
        }

        static Verification expired() {
            return new Verification(Outcome.EXPIRED, 0);
        }
    }
}
