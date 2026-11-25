package com.bhoomi.paycore.card;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The card vault's cryptography.
 * <ul>
 *   <li>Card numbers are encrypted with AES-256-GCM (random 96-bit IV per card, authenticated).</li>
 *   <li>Lookups use a keyed fingerprint (HMAC-SHA256), so the database can find a card by number
 *       without being able to reverse the fingerprint into a number.</li>
 *   <li>The CVC is derived from the number and expiry with a separate key, the way issuers compute
 *       CVV2. It is never stored anywhere.</li>
 * </ul>
 * Keys come from configuration (environment variables in production).
 */
@Component
public class CardCrypto {

    private static final String AES = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec fingerprintKey;
    private final SecretKeySpec cvcKey;
    private final SecureRandom random = new SecureRandom();

    public CardCrypto(@Value("${paycore.cards.encryption-key}") String encryptionKey,
                      @Value("${paycore.cards.fingerprint-key}") String fingerprintKey,
                      @Value("${paycore.cards.cvc-key}") String cvcKey) {
        this.encryptionKey = new SecretKeySpec(decodeKey(encryptionKey, "encryption-key"), "AES");
        this.fingerprintKey = new SecretKeySpec(decodeKey(fingerprintKey, "fingerprint-key"), "HmacSHA256");
        this.cvcKey = new SecretKeySpec(decodeKey(cvcKey, "cvc-key"), "HmacSHA256");
    }

    public String encrypt(String pan) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(pan.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Card encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance(AES);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Card decryption failed", e);
        }
    }

    public String fingerprint(String pan) {
        return HexFormat.of().formatHex(hmac(fingerprintKey, pan));
    }

    /** Three-digit security code derived from number + expiry. Same inputs always give the same code. */
    public String cvc(String pan, int expMonth, int expYear) {
        byte[] mac = hmac(cvcKey, pan + "|" + expMonth + "|" + expYear);
        int value = ((mac[0] & 0xff) << 16 | (mac[1] & 0xff) << 8 | (mac[2] & 0xff)) % 1000;
        return String.format("%03d", value);
    }

    private static byte[] hmac(SecretKeySpec key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    private static byte[] decodeKey(String base64, String name) {
        byte[] key = Base64.getDecoder().decode(base64);
        if (key.length != 32) {
            throw new IllegalArgumentException("paycore.cards." + name + " must be 32 bytes, base64-encoded");
        }
        return key;
    }
}
