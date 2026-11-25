package com.bhoomi.paycore.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window rate limit per client IP, counted in Redis so it holds across app instances.
 * Two buckets: a strict one for sign-up/login/guest (slows password guessing and account spam),
 * and a general one for the API.
 *
 * <p>Runs before Spring Security, so rejected logins are counted too.
 * Deliberately fails OPEN: if Redis is down, requests are let through. Rate limiting protects
 * capacity, not money, so availability wins here. The ledger, by contrast, fails CLOSED.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final long apiPerMinute;
    private final long authPerMinute;

    public RateLimitFilter(StringRedisTemplate redis,
                           @Value("${paycore.rate-limit.requests-per-minute:120}") long apiPerMinute,
                           @Value("${paycore.rate-limit.auth-per-minute:10}") long authPerMinute) {
        this.redis = redis;
        this.apiPerMinute = apiPerMinute;
        this.authPerMinute = authPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return bucket(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String bucket = bucket(request);
        long limit = "auth".equals(bucket) ? authPerMinute : apiPerMinute;
        long window = Instant.now().getEpochSecond() / WINDOW.toSeconds();
        String key = "ratelimit:" + bucket + ":" + request.getRemoteAddr() + ":" + window;

        Long count = null;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, WINDOW);
            }
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
        }

        if (count != null && count > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,"
                    + "\"detail\":\"Rate limit of " + limit + " requests per minute exceeded\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Which bucket a request counts against, or null if it isn't rate limited. */
    private static String bucket(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean authWrite = path.startsWith("/auth/") && "POST".equals(request.getMethod()) && !path.equals("/auth/logout");
        // Code entry gets the strict bucket too: a second layer against guessing, on top of the 3-attempt limit.
        authWrite = authWrite || (path.startsWith("/payments/") && path.endsWith("/authenticate"));
        // Card entry too: slows down anyone trying card numbers or CVCs.
        authWrite = authWrite || (path.startsWith("/checkout-sessions/") && (path.endsWith("/pay") || path.endsWith("/authenticate")));
        if (authWrite) {
            return "auth";
        }
        if (path.startsWith("/accounts") || path.startsWith("/payments") || path.startsWith("/ledger")
                || path.startsWith("/cards") || path.startsWith("/activity") || path.startsWith("/notifications") || path.startsWith("/checkout-sessions")
                || path.startsWith("/webhooks/endpoints") || path.startsWith("/webhooks/deliveries")) {
            return "api";
        }
        return null;
    }
}
