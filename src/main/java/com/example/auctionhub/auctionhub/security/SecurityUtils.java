package com.example.auctionhub.auctionhub.security;

import com.example.auctionhub.auctionhub.models.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

public class SecurityUtils {

    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }
        // If using a custom UserDetails, adapt this section:
        // if (principal instanceof CustomUserDetails) {
        //     return ((CustomUserDetails) principal).getUser();
        // }
        return null;
    }

    public static User getAuthenticatedUserOrThrow() {
        User user = getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("User not authenticated");
        }
        return user;
    }
}
