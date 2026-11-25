package com.bhoomi.paycore.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "paycore.rate-limit.requests-per-minute=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void fourthRequestInAMinuteFromOneClientIsRejected() throws Exception {
        String clientIp = "10.0.0." + (1 + (UUID.randomUUID().hashCode() & 0x7f)) + "-" + UUID.randomUUID();
        String unknownAccount = "/accounts/" + UUID.randomUUID();

        // Not signed in, so each request is a 401, but the limiter runs first and still counts them.
        for (int i = 0; i < 3; i++) {
            mvc.perform(from(clientIp, get(unknownAccount))).andExpect(status().isUnauthorized());
        }
        mvc.perform(from(clientIp, get(unknownAccount)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void healthChecksAreNeverRateLimited() throws Exception {
        String clientIp = "rate-limit-health-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            mvc.perform(from(clientIp, get("/actuator/health"))).andExpect(status().isOk());
        }
    }

    private static MockHttpServletRequestBuilder from(String ip, MockHttpServletRequestBuilder request) {
        return request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
    }
}
