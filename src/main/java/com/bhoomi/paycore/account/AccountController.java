package com.bhoomi.paycore.account;

import com.bhoomi.paycore.common.IdempotencyKeys;
import com.bhoomi.paycore.ledger.AccountBalance;
import com.bhoomi.paycore.ledger.AccountType;
import com.bhoomi.paycore.ledger.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(request.type(), request.name(), Currency.INR);
        return AccountResponse.of(account, accountService.balance(account.getId()));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.of(accountService.get(id), accountService.balance(id));
    }

    /** Adds money from outside PayCore; stands in for a confirmed bank or card deposit. */
    @PostMapping("/{id}/top-ups")
    public TopUpResponse topUp(@PathVariable UUID id,
                               @RequestHeader(IdempotencyKeys.HEADER) String idempotencyKey,
                               @Valid @RequestBody TopUpRequest request) {
        TopUp topUp = accountService.topUp(IdempotencyKeys.validate(idempotencyKey), id, request.amount());
        return new TopUpResponse(topUp.getId(), topUp.getAccountId(), topUp.getAmount(),
                topUp.getStatus().name(), accountService.balance(id));
    }

    public record CreateAccountRequest(@NotNull AccountType type, @NotBlank @Size(max = 200) String name) {
    }

    public record TopUpRequest(@Positive long amount) {
    }

    public record AccountResponse(UUID id, AccountType type, String name, Currency currency,
                                  Instant createdAt, AccountBalance balance) {
        static AccountResponse of(Account a, AccountBalance balance) {
            return new AccountResponse(a.getId(), a.getType(), a.getName(), a.getCurrency(), a.getCreatedAt(), balance);
        }
    }

    public record TopUpResponse(UUID id, UUID accountId, long amount, String status, AccountBalance balance) {
    }
}
