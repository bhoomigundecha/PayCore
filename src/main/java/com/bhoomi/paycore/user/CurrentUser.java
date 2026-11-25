package com.bhoomi.paycore.user;

import java.io.Serializable;
import java.util.UUID;

/**
 * The signed-in user, as stored in the session (in Redis). Deliberately holds no password hash.
 * Controllers receive it with {@code @AuthenticationPrincipal CurrentUser me}.
 */
public record CurrentUser(UUID id, String email, String name, boolean guest) implements Serializable {

    static CurrentUser of(User user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getName(), user.isGuest());
    }
}
