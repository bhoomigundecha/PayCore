package com.bhoomi.paycore.card;

import com.bhoomi.paycore.user.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cards")
public class CardController {

    private final CardService cards;

    public CardController(CardService cards) {
        this.cards = cards;
    }

    /** Your cards, masked: last 4 digits only. */
    @GetMapping
    public List<CardView> list(@AuthenticationPrincipal CurrentUser me) {
        return cards.listFor(me.id()).stream().map(c -> CardView.of(c, me.name())).toList();
    }

    /** Full number and CVC, for the owner only. POST (not GET) and no-store so it is never cached. */
    @PostMapping("/{id}/reveal")
    public ResponseEntity<CardService.RevealedCard> reveal(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cards.reveal(id, me.id()));
    }

    public record CardView(UUID id, UUID accountId, String brand, String last4, int expMonth, int expYear,
                           String holderName, Card.Status status) {
        static CardView of(Card c, String holderName) {
            return new CardView(c.getId(), c.getAccountId(), c.getBrand(), c.getLast4(), c.getExpMonth(),
                    c.getExpYear(), holderName, c.getStatus());
        }
    }
}
