package com.bhoomi.paycore.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Who may call what.
 * <ul>
 *   <li>Public: the web app itself, API docs, health, sign-up/login, and the aggregate ledger stats.</li>
 *   <li>Signed in: everything that touches a specific account, card or payment. Ownership is then
 *       checked in the services (you only ever see your own accounts).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository) throws Exception {
        http
                .csrf(csrf -> csrf.spa() // XSRF-TOKEN cookie, echoed back by the React app as X-XSRF-TOKEN
                        // Server-to-server webhook calls carry no cookies; they are authenticated by signature.
                        .ignoringRequestMatchers("/webhooks/receiver/**"))
                .securityContext(context -> context.securityContextRepository(contextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/ledger/overview", "/ledger/totals", "/activity", "/checkout-sessions/*").permitAll()
                        // Paying a link is public: the card is the credential (rate-limited, CSRF-protected).
                        .requestMatchers(HttpMethod.POST, "/checkout-sessions/*/pay", "/checkout-sessions/*/authenticate").permitAll()
                        .requestMatchers("/checkout-sessions", "/checkout-sessions/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/webhooks/receiver/*").permitAll()
                        .requestMatchers("/webhooks/**").authenticated()
                        .requestMatchers("/accounts/**", "/payments/**", "/cards/**", "/ledger/**", "/notifications/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Login is handled by AuthService; this stops Spring Boot from creating a default in-memory user. */
    @Bean
    UserDetailsService noDefaultUser() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }
}
