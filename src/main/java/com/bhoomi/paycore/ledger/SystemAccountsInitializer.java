package com.bhoomi.paycore.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures PayCore's system accounts exist on every startup. Idempotent: after the first
 * run TigerBeetle answers Exists. If the ledger is unreachable the app fails to start (fail closed).
 */
@Component
public class SystemAccountsInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemAccountsInitializer.class);

    private final LedgerService ledgerService;

    public SystemAccountsInitializer(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Currency currency : Currency.values()) {
            ledgerService.createAccount(SystemAccounts.externalFunding(currency), AccountType.EXTERNAL_FUNDING, currency);
            ledgerService.createAccount(SystemAccounts.platformFee(currency), AccountType.PLATFORM_FEE, currency);
        }
        log.info("System ledger accounts ready");
    }
}
