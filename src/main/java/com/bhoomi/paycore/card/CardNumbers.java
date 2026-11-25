package com.bhoomi.paycore.card;

import java.security.SecureRandom;

/**
 * Card number (PAN) helpers. PayCore issues numbers in its own test range: they start with
 * {@link #BIN}, are 16 digits long and end in a Luhn check digit, like real card numbers.
 */
public final class CardNumbers {

    /** Issuer prefix for PayCore test cards. "99" is outside the Visa/Mastercard/RuPay/Amex ranges. */
    public static final String BIN = "990001";
    public static final int LENGTH = 16;

    /** A public test card that is always declined ("do not honor"), like issuers' test numbers. Never issued. */
    public static final String TEST_DECLINE = "9900010000000001";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CardNumbers() {
    }

    /** A new random PayCore card number with a valid Luhn check digit. */
    public static String generate() {
        while (true) {
            StringBuilder pan = new StringBuilder(BIN);
            while (pan.length() < LENGTH - 1) {
                pan.append(RANDOM.nextInt(10));
            }
            pan.append(checkDigit(pan));
            if (!pan.toString().equals(TEST_DECLINE)) {
                return pan.toString();
            }
        }
    }

    /** Luhn check: catches every single-digit typo and most swapped adjacent digits. */
    public static boolean isValid(String pan) {
        if (pan == null || !pan.matches("\\d{12,19}")) {
            return false;
        }
        return checkDigit(pan.substring(0, pan.length() - 1)) == pan.charAt(pan.length() - 1) - '0';
    }

    public static boolean isPayCoreCard(String pan) {
        return pan != null && pan.length() == LENGTH && pan.startsWith(BIN) && isValid(pan);
    }

    /** Strips spaces and dashes a user might type. */
    public static String normalize(String input) {
        return input == null ? "" : input.replaceAll("[\\s-]", "");
    }

    static int checkDigit(CharSequence payload) {
        int sum = 0;
        boolean doubleIt = true; // the digit next to the (missing) check digit is doubled
        for (int i = payload.length() - 1; i >= 0; i--) {
            int d = payload.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }
}
