package com.bhoomi.paycore.card;

import com.bhoomi.paycore.common.NotFoundException;
import com.bhoomi.paycore.ledger.LedgerService;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CardService {

    private static final int VALID_YEARS = 4;

    private final CardRepository cards;
    private final CardCrypto crypto;

    public CardService(CardRepository cards, CardCrypto crypto) {
        this.cards = cards;
        this.crypto = crypto;
    }

    /** Issues a new virtual card for a customer wallet. */
    public Card issue(UUID userId, UUID walletAccountId) {
        YearMonth expiry = YearMonth.now().plusYears(VALID_YEARS);
        for (int attempt = 0; attempt < 5; attempt++) {
            String pan = CardNumbers.generate();
            Card card = new Card(LedgerService.newId(), walletAccountId, userId, pan.substring(pan.length() - 4),
                    expiry.getMonthValue(), expiry.getYear(), crypto.encrypt(pan), crypto.fingerprint(pan));
            try {
                return cards.saveAndFlush(card);
            } catch (DataIntegrityViolationException e) {
                // Astronomically unlikely number collision (unique fingerprint); draw a new number.
            }
        }
        throw new IllegalStateException("Could not issue a unique card number");
    }

    public List<Card> listFor(UUID userId) {
        return cards.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Full number and CVC for the card's owner only. Nothing here is logged or stored. */
    public RevealedCard reveal(UUID cardId, UUID userId) {
        Card card = cards.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> new NotFoundException("Card not found: " + cardId));
        String pan = crypto.decrypt(card.getPanEncrypted());
        return new RevealedCard(card.getId(), pan, crypto.cvc(pan, card.getExpMonth(), card.getExpYear()),
                card.getExpMonth(), card.getExpYear());
    }

    public record RevealedCard(UUID id, String number, String cvc, int expMonth, int expYear) {
    }
}
