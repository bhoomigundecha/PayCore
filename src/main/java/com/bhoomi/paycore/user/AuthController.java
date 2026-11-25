package com.bhoomi.paycore.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session-based auth for the web app. The session lives in Redis (Spring Session) and is identified
 * by an httpOnly cookie, so JavaScript never sees a token. State-changing requests carry a CSRF token.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;
    private final SecurityContextRepository contextRepository;

    public AuthController(AuthService auth, SecurityContextRepository contextRepository) {
        this.auth = auth;
        this.contextRepository = contextRepository;
    }

    /** Who am I? Also makes sure the browser has a CSRF cookie before its first POST. */
    @GetMapping("/me")
    public Session me(@AuthenticationPrincipal CurrentUser me, CsrfToken csrf) {
        csrf.getToken(); // touching the token writes the XSRF-TOKEN cookie
        return new Session(me != null, me);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Session signUp(@Valid @RequestBody SignUpRequest body, HttpServletRequest request, HttpServletResponse response) {
        return signIn(auth.signUp(body.name(), body.email(), body.password()), request, response);
    }

    @PostMapping("/login")
    public Session login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        return signIn(auth.authenticate(body.email(), body.password()), request, response);
    }

    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    public Session guest(HttpServletRequest request, HttpServletResponse response) {
        return signIn(auth.guest(), request, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private Session signIn(CurrentUser user, HttpServletRequest request, HttpServletResponse response) {
        // New session id on every sign-in, so a session id planted before login is useless (session fixation).
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return new Session(true, user);
    }

    public record Session(boolean authenticated, CurrentUser user) {
    }

    public record SignUpRequest(@NotBlank @Size(max = 100) String name,
                                @NotBlank @Email @Size(max = 320) String email,
                                @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {
    }
}
