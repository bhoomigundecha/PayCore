package com.bhoomi.paycore.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI payCoreOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PayCore API")
                .version("1.0")
                .description("""
                        Payment processing backend on a double-entry TigerBeetle ledger.

                        Try it: create a CUSTOMER_WALLET and a MERCHANT account, top up the wallet, \
                        then create a payment. Amounts are in paise (10000 = ₹100). \
                        POST requests that move money need an Idempotency-Key header (any unique string); \
                        resend the same key to see that money only moves once."""));
    }
}
