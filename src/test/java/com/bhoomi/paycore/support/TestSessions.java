package com.bhoomi.paycore.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Signs users in through the real auth endpoints and keeps their session for later requests. */
public final class TestSessions {

    private TestSessions() {
    }

    /** A signed-in user: the SESSION cookie (Spring Session keeps the session itself in Redis). */
    public record SignedIn(Cookie sessionCookie, UUID userId) {

        /** The request, sent as this user (session cookie + CSRF token). */
        public MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request) {
            return request.cookie(sessionCookie).with(csrf());
        }
    }

    public static SignedIn guest(MockMvc mvc) throws Exception {
        return signIn(mvc.perform(post("/auth/guest").with(csrf())).andReturn());
    }

    public static SignedIn signUp(MockMvc mvc, String email, String password) throws Exception {
        return signIn(mvc.perform(post("/auth/signup").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn());
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    private static SignedIn signIn(MvcResult result) throws Exception {
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Sign-in failed: " + result.getResponse().getStatus() + " "
                    + result.getResponse().getContentAsString());
        }
        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.user.id");
        Cookie cookie = result.getResponse().getCookie("SESSION");
        if (cookie == null) {
            throw new IllegalStateException("No SESSION cookie after sign-in");
        }
        return new SignedIn(cookie, UUID.fromString(id));
    }
}
