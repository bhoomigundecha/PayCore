package com.bhoomi.paycore.webhook;

import com.bhoomi.paycore.common.InvalidRequestException;
import com.bhoomi.paycore.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Webhook endpoints of your stores, their delivery log, and the built-in test receiver's inbox. */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService webhooks;
    private final TestReceiver receiver;

    public WebhookController(WebhookService webhooks, TestReceiver receiver) {
        this.webhooks = webhooks;
        this.receiver = receiver;
    }

    public record CreateRequest(@NotNull UUID accountId, String url, Boolean testReceiver) {
    }

    public record EndpointView(UUID id, UUID accountId, String url, boolean testReceiver, boolean failing,
                               java.time.Instant createdAt) {
    }

    public record CreatedView(EndpointView endpoint, String secret) {
    }

    public record ReceiverMode(@NotNull Boolean failing) {
    }

    @GetMapping("/endpoints")
    public List<EndpointView> list(@AuthenticationPrincipal CurrentUser me) {
        return webhooks.list(me.id()).stream().map(this::view).toList();
    }

    @PostMapping("/endpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedView create(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody CreateRequest body) {
        boolean test = Boolean.TRUE.equals(body.testReceiver());
        if (!test && (body.url() == null || body.url().isBlank())) {
            throw new InvalidRequestException("Give a url, or set testReceiver to use PayCore's built-in receiver");
        }
        var created = webhooks.create(me.id(), body.accountId(), body.url(), test);
        return new CreatedView(view(created.endpoint()), created.secret());
    }

    @DeleteMapping("/endpoints/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        webhooks.delete(id, me.id());
    }

    @GetMapping("/endpoints/{id}/deliveries")
    public List<WebhookService.Delivery> deliveries(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return webhooks.deliveries(id, me.id());
    }

    @PostMapping("/endpoints/{id}/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, UUID> sendTest(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return Map.of("eventId", webhooks.sendTest(id, me.id()));
    }

    /** What the built-in receiver got, newest first. */
    @GetMapping("/endpoints/{id}/received")
    public List<TestReceiver.Received> received(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        requireTestReceiver(id, me);
        return receiver.recent(id);
    }

    /** Make the built-in receiver answer 503, to watch PayCore retry with backoff. */
    @PutMapping("/endpoints/{id}/receiver")
    public EndpointView setMode(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id,
                                @Valid @RequestBody ReceiverMode body) {
        var endpoint = requireTestReceiver(id, me);
        receiver.setFailing(id, body.failing());
        return view(endpoint);
    }

    @PostMapping("/deliveries/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retry(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        webhooks.retry(id, me.id());
    }

    private WebhookService.Endpoint requireTestReceiver(UUID id, CurrentUser me) {
        var endpoint = webhooks.requireOwned(id, me.id());
        if (!endpoint.testReceiver()) {
            throw new InvalidRequestException("This endpoint is your own server, not PayCore's test receiver");
        }
        return endpoint;
    }

    private EndpointView view(WebhookService.Endpoint e) {
        return new EndpointView(e.id(), e.accountId(), e.url(), e.testReceiver(),
                e.testReceiver() && receiver.isFailing(e.id()), e.createdAt());
    }
}
