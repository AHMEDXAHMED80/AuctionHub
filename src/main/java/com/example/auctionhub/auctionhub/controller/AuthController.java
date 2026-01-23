package com.example.auctionhub.auctionhub.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.auctionhub.auctionhub.constants.ApiConstants;
import com.example.auctionhub.auctionhub.dto.AuthInternalResult;
import com.example.auctionhub.auctionhub.dto.AuthLoginRequest;
import com.example.auctionhub.auctionhub.dto.AuthRegisterRequest;
import com.example.auctionhub.auctionhub.dto.AuthResponse;
import com.example.auctionhub.auctionhub.dto.TokenPairResponse;
import com.example.auctionhub.auctionhub.service.AuthService;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Controller for handling authentication endpoints, including registration and login.
 * Manages JWT cookie creation and delegates authentication logic to AuthService.
 */
@RestController
@RequestMapping(ApiConstants.AUTH_BASE)
public class AuthController {
    @Value("${auth.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${auth.cookie.sameSite:Strict}")
    private String cookieSameSite;
    private final AuthService authService;


    /**
     * Constructs an AuthController with required AuthService dependency.
     *
     * @param authService Service for authentication logic
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user and sets JWT cookies in the response.
     *
     * @param request  Registration request body
     * @param response HttpServletResponse for setting cookies
     * @return AuthResponse with registration result
     */
    @PostMapping(ApiConstants.AUTH_REGISTER)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRegisterRequest request, HttpServletResponse response) {
        AuthInternalResult initialResponse = authService.registerUser(request);
        String accessToken = initialResponse.getAccessToken();
        String refreshToken = initialResponse.getRefreshToken();
        ResponseCookie accessCookie = ResponseCookie.from("access_token", accessToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(15 * 60)
            .build();
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(7 * 24 * 60 * 60)
            .build();
        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
        AuthResponse finalResponse = new AuthResponse(initialResponse.getUserId(), initialResponse.getUsername(), "registration sucessful");
        return ResponseEntity.status(HttpStatus.CREATED).body(finalResponse);
    }

    @PostMapping(ApiConstants.AUTH_LOGIN)
    public ResponseEntity<AuthResponse> Login(@Valid @RequestBody AuthLoginRequest request, HttpServletResponse response) {
        
        AuthInternalResult initialResponse = authService.login(request);
        String accessToken = initialResponse.getAccessToken();
        String refreshToken = initialResponse.getRefreshToken();

        ResponseCookie accessCookie = ResponseCookie.from("access_token", accessToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(15 * 60)
            .build();
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(7 * 24 * 60 * 60)
            .build();
        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
        AuthResponse finalResponse = new AuthResponse(initialResponse.getUserId(), initialResponse.getUsername(), "Login sucessful");
        return ResponseEntity.status(HttpStatus.CREATED).body(finalResponse);
    }

    @PostMapping(ApiConstants.AUTH_LOGOUT)
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
            String accessToken = null;
            String refreshToken = null;

        try {
            if (request.getCookies() != null) {
                for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                    if ("access_token".equals(cookie.getName())) {
                        accessToken = cookie.getValue();
                    }
                    if ("refresh_token".equals(cookie.getName())) {
                        refreshToken = cookie.getValue();
                    }
                }
            }

            authService.logout(accessToken, refreshToken);
        } catch (Exception e) {
            // Log the exception for debugging
            System.err.println("Logout error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Always clear cookies
            ResponseCookie accessCookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
            ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
            response.addHeader("Set-Cookie", accessCookie.toString());
            response.addHeader("Set-Cookie", refreshCookie.toString());
        }
        return ResponseEntity.ok().build();

    }

    @GetMapping(ApiConstants.AUTH_CHECK_USERNAME)
    public ResponseEntity<Boolean> checkUsername(@PathVariable String username) {
        boolean available = authService.isUsernameAvailable(username);
        return ResponseEntity.ok(available);
    }

    @GetMapping(ApiConstants.AUTH_CHECK_EMAIL)
    public ResponseEntity<Boolean> checkEmail(@PathVariable String email) {
        boolean available = authService.isEmailAvailable(email);
        return ResponseEntity.ok(available);
    }

    @PostMapping(ApiConstants.AUTH_REFRESH)
    public ResponseEntity<TokenPairResponse> refreshRefreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("refresh_token".equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        TokenPairResponse newRefreshToken = authService.issueNewTokens(refreshToken);
        ResponseCookie accessCookie = ResponseCookie.from("access_token", newRefreshToken.getAccessToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(15 * 60)
            .build();
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", newRefreshToken.getRefreshToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(cookieSameSite)
            .path("/")
            .maxAge(7 * 24 * 60 * 60)
            .build();
        response.addHeader("Set-Cookie", accessCookie.toString());
        response.addHeader("Set-Cookie", refreshCookie.toString());
        return ResponseEntity.ok(newRefreshToken);
    }

}
