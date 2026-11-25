package com.bhoomi.paycore.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class CardNumbersTest {

    @RepeatedTest(200)
    void generatedNumbersAreValidPayCoreCards() {
        String pan = CardNumbers.generate();
        assertThat(pan).hasSize(16).startsWith(CardNumbers.BIN);
        assertThat(CardNumbers.isValid(pan)).isTrue();
        assertThat(CardNumbers.isPayCoreCard(pan)).isTrue();
    }

    @Test
    void luhnMatchesKnownPublicTestNumbers() {
        assertThat(CardNumbers.isValid("4242424242424242")).isTrue();
        assertThat(CardNumbers.isValid("5555555555554444")).isTrue();
        assertThat(CardNumbers.isValid("378282246310005")).isTrue();
        assertThat(CardNumbers.isValid("4242424242424241")).isFalse();
    }

    @Test
    void anySingleDigitTypoIsCaught() {
        String pan = CardNumbers.generate();
        for (int i = 0; i < pan.length(); i++) {
            for (char d = '0'; d <= '9'; d++) {
                if (d == pan.charAt(i)) {
                    continue;
                }
                String typo = pan.substring(0, i) + d + pan.substring(i + 1);
                assertThat(CardNumbers.isValid(typo)).as(typo).isFalse();
            }
        }
    }

    @Test
    void otherNetworksAreNotPayCoreCards() {
        assertThat(CardNumbers.isPayCoreCard("4242424242424242")).isFalse();
        assertThat(CardNumbers.isPayCoreCard(null)).isFalse();
        assertThat(CardNumbers.normalize("9900 0112-3456 7890")).isEqualTo("9900011234567890");
    }
}
