package org.ewsfi.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** The authenticated local-dev user, used as the disposition and case actor. */
public final class LocalActor {

    private LocalActor() {}

    public static String id() {
        return authentication().getName();
    }

    public static String role() {
        boolean supervisor =
                authentication().getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_SUPERVISOR".equals(authority.getAuthority()));
        return supervisor ? "SUPERVISOR" : "ANALYST";
    }

    public static boolean isSupervisor() {
        return "SUPERVISOR".equals(role());
    }

    private static Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated analyst");
        }
        return authentication;
    }
}
