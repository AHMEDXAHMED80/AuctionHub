package com.example.auctionhub.auctionhub.security;

import java.io.IOException;
import java.util.Collections;

import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.http.Cookie;

import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.redis.TokenBlackListService;
import com.example.auctionhub.auctionhub.repository.UserRepository;
// import com.example.auctionhub.auctionhub.model.User; // Removed or update with correct package if needed

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(2) 
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlackListService tokenBlackListService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenBlackListService tokenBlackListService, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.tokenBlackListService = tokenBlackListService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain
    )throws ServletException, IOException{
        String token = null;
        
        // First, try to get token from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // Remove "Bearer " prefix
        }
        
        // If not in header, try to get from cookie (for browser-based requests)
        if (token == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token != null && jwtUtil.validateAccessToken(token)) {
            String jti = jwtUtil.extractId(token);
            if (tokenBlackListService.isTokenBlackListed(jti)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
                return;
            }

            Long userId = jwtUtil.extractUserId(token);
            String role = jwtUtil.extractRole(token);

            User user = userRepository.findById(userId).orElseThrow(
                () -> new IllegalArgumentException("User not found")
            );

            if (user.getUserStatus().equals(User.status.blocked)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "User is blocked");
                return;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
            );
            authentication.setDetails(user);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        
        // ALWAYS continue filter chain (for ALL requests)
        filterChain.doFilter(request, response);
    }
    
}
