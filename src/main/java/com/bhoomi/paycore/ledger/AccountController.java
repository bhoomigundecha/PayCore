package com.bhoomi.paycore.ledger;

import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final LedgerService ledgerService;

    public AccountController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public CreateAccountResponse create(@RequestParam AccountType type) {
        UUID id = ledgerService.createAccount(type, Currency.INR);
        return new CreateAccountResponse(id, type);
    }

    public record CreateAccountResponse(UUID id, AccountType type) {
    }
}