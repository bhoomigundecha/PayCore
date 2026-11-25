package com.bhoomi.paycore.stepup;

import com.bhoomi.paycore.user.CurrentUser;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's simulated phone: where test-mode one-time codes arrive. */
@RestController
public class NotificationController {

    private final PhoneInbox inbox;

    public NotificationController(PhoneInbox inbox) {
        this.inbox = inbox;
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<PhoneInbox.Message>> list(@AuthenticationPrincipal CurrentUser me) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inbox.read(me.id()));
    }
}
