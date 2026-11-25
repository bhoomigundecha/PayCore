package com.bhoomi.paycore.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bhoomi.paycore.common.InvalidRequestException;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** Signatures and SSRF rules, no Spring needed. */
class WebhookSecurityTest {

    private final UrlGuard production = new UrlGuard(false);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://93.184.215.14/hook",              // plain http
            "https://127.0.0.1/hook",                 // loopback
            "https://localhost:6379/",                // Redis on the same box
            "https://169.254.169.254/latest/meta-data", // cloud metadata service
            "https://10.0.0.5/hook",                  // private network
            "https://172.28.0.10:3000/",              // the TigerBeetle container
            "https://192.168.1.1/",
            "https://100.64.0.1/",                    // carrier-grade NAT
            "https://0.0.0.0/",
            "https://[::1]/hook",
            "https://[fd00::1]/hook",                 // IPv6 unique local
            "https://[::ffff:127.0.0.1]/hook",        // IPv4-mapped loopback
            "https://user:pass@93.184.215.14/hook",   // credentials in the URL
            "ftp://93.184.215.14/",
            "not a url"})
    void rejectsUrlsThatReachInsideOrAreUnsafe(String url) {
        assertThatThrownBy(() -> production.check(url)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void acceptsAPublicHttpsAddress() {
        assertThat(production.check("https://93.184.215.14/webhooks/paycore").getHost()).isEqualTo("93.184.215.14");
    }

    @Test
    void localDevelopmentMayUseLocalhost() {
        assertThat(new UrlGuard(true).check("http://localhost:3000/hook").getPort()).isEqualTo(3000);
    }

    @Test
    void signatureVerifiesOnlyTheExactBodyWithTheRightSecretWhileFresh() {
        String secret = WebhookSigner.newSecret();
        String body = "{\"type\":\"payment.succeeded\"}";
        long now = Instant.now().getEpochSecond();
        String header = WebhookSigner.header(secret, now, body);

        assertThat(WebhookSigner.verify(secret, header, body, now)).isTrue();
        assertThat(WebhookSigner.verify(secret, header, body.replace("succeeded", "refunded"), now)).isFalse();
        assertThat(WebhookSigner.verify(WebhookSigner.newSecret(), header, body, now)).isFalse();
        // A captured request replayed ten minutes later is refused.
        assertThat(WebhookSigner.verify(secret, header, body, now + 600)).isFalse();
        assertThat(WebhookSigner.verify(secret, null, body, now)).isFalse();
        assertThat(WebhookSigner.verify(secret, "t=abc,v1=00", body, now)).isFalse();
    }
}
