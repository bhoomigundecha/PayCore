package com.bhoomi.paycore.card;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.YearMonth;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Checks card details typed into a checkout form, the way an issuer would, and answers with a
 * decline code when something is wrong. Repeated wrong expiry/CVC attempts lock the card for an hour,
 * so a stolen number can't be completed by guessing the CVC.
 */
@Component
public class CardVerifier {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_WINDOW = Duration.ofHours(1);

    private final CardRepository cards;
    private final CardCrypto crypto;
    private final StringRedisTemplate redis;

    public CardVerifier(CardRepository cards, CardCrypto crypto, StringRedisTemplate redis) {
        this.cards = cards;
        this.crypto = crypto;
        this.redis = redis;
    }

    public Card verify(String number, int expMonth, int expYear, String cvc) {
        String pan = CardNumbers.normalize(number);
        if (!CardNumbers.isValid(pan)) {
            throw new CardDeclinedException("incorrect_number", "The card number is incorrect.");
        }
        if (pan.equals(CardNumbers.TEST_DECLINE)) {
            throw new CardDeclinedException("card_declined", "The card was declined by the issuer.");
        }
        if (!pan.startsWith(CardNumbers.BIN)) {
            throw new CardDeclinedException("card_not_supported", "Only PayCore test cards are accepted here.");
        }

        int year = expYear < 100 ? 2000 + expYear : expYear;
        if (expMonth < 1 || expMonth > 12 || year < 2000) {
            throw new CardDeclinedException("invalid_expiry", "The expiry date is invalid.");
        }
        if (YearMonth.of(year, expMonth).isBefore(YearMonth.now())) {
            throw new CardDeclinedException("expired_card", "The card has expired.");
        }

        String fingerprint = crypto.fingerprint(pan);
        Card card = cards.findByPanFingerprint(fingerprint)
                .orElseThrow(() -> new CardDeclinedException("incorrect_number", "The card number is incorrect."));

        String failuresKey = "card:failures:" + fingerprint;
        String failures = redis.opsForValue().get(failuresKey);
        if (card.getStatus() != Card.Status.ACTIVE || (failures != null && Integer.parseInt(failures) >= MAX_FAILURES)) {
            throw new CardDeclinedException("card_declined", "The card was declined by the issuer.");
        }

        boolean expiryMatches = card.getExpMonth() == expMonth && card.getExpYear() == year;
        String expected = crypto.cvc(pan, card.getExpMonth(), card.getExpYear());
        boolean cvcMatches = cvc != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), cvc.trim().getBytes(StandardCharsets.US_ASCII));
        if (!expiryMatches || !cvcMatches) {
            Long count = redis.opsForValue().increment(failuresKey);
            if (count != null && count == 1) {
                redis.expire(failuresKey, LOCK_WINDOW);
            }
            throw expiryMatches
                    ? new CardDeclinedException("incorrect_cvc", "The security code is incorrect.")
                    : new CardDeclinedException("invalid_expiry", "The expiry date doesn't match the card.");
        }
        redis.delete(failuresKey);
        return card;
    }
}
