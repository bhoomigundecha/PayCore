package com.bhoomi.paycore.user;

import com.bhoomi.paycore.account.Account;
import com.bhoomi.paycore.account.AccountService;
import com.bhoomi.paycore.card.CardService;
import com.bhoomi.paycore.common.IdempotencyKeys;
import com.bhoomi.paycore.common.InvalidStateException;
import com.bhoomi.paycore.common.UnauthorizedException;
import com.bhoomi.paycore.ledger.AccountType;
import com.bhoomi.paycore.ledger.Currency;
import com.bhoomi.paycore.ledger.LedgerService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** Play money every new user starts with: ₹5,000. */
    static final long WELCOME_CREDIT = 500_000;

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AccountService accounts;
    private final CardService cards;
    /** Compared against when the email doesn't exist, so login takes the same time either way. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, AccountService accounts, CardService cards) {
        this.users = users;
        this.passwords = passwords;
        this.accounts = accounts;
        this.cards = cards;
        this.dummyHash = passwords.encode("timing-equaliser");
    }

    public CurrentUser signUp(String name, String email, String password) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
        User user;
        try {
            user = users.saveAndFlush(new User(LedgerService.newId(), normalized, name.trim(), passwords.encode(password), false));
        } catch (DataIntegrityViolationException e) {
            throw emailTaken(); // lost a race with a concurrent sign-up
        }
        onboard(user);
        return CurrentUser.of(user);
    }

    /** One-click demo user: no email or password, same starter kit as a real sign-up. */
    public CurrentUser guest() {
        UUID id = LedgerService.newId();
        User user = users.saveAndFlush(new User(id, "guest-" + id + "@guest.paycore.local", "Guest", null, true));
        onboard(user);
        return CurrentUser.of(user);
    }

    public CurrentUser authenticate(String email, String password) {
        User user = users.findByEmail(normalize(email)).filter(u -> u.getPasswordHash() != null).orElse(null);
        boolean matches = passwords.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !matches) {
            // Same message for "no such user" and "wrong password", so emails can't be enumerated.
            throw new UnauthorizedException("Wrong email or password");
        }
        return CurrentUser.of(user);
    }

    /** Wallet with a virtual card and welcome credit, plus a store of their own to receive payments. */
    private void onboard(User user) {
        String first = user.getName().split("\\s+")[0];
        Account wallet = accounts.create(user.getId(), AccountType.CUSTOMER_WALLET, first + "'s wallet", Currency.INR);
        cards.issue(user.getId(), wallet.getId());
        accounts.topUp(IdempotencyKeys.scoped(user.getId(), "welcome-credit"), wallet.getId(), WELCOME_CREDIT);
        accounts.create(user.getId(), AccountType.MERCHANT, first + "'s store", Currency.INR);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static InvalidStateException emailTaken() {
        return new InvalidStateException("An account with this email already exists");
    }
}
