package com.example.auctionhub.auctionhub.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.auctionhub.auctionhub.dto.AuthInternalResult;
import com.example.auctionhub.auctionhub.dto.AuthLoginRequest;
import com.example.auctionhub.auctionhub.dto.AuthRegisterRequest;
import com.example.auctionhub.auctionhub.dto.TokenPairResponse;
import com.example.auctionhub.auctionhub.mapper.UserMapper;
import com.example.auctionhub.auctionhub.models.User;
import com.example.auctionhub.auctionhub.redis.TokenActiveList;
import com.example.auctionhub.auctionhub.redis.TokenBlackListService;
import com.example.auctionhub.auctionhub.repository.UserRepository;
import com.example.auctionhub.auctionhub.security.JwtUtil;

/**
 * Service for handling authentication operations, including user registration, login, and token management.
 * Manages password encoding, JWT generation, and token blacklisting.
 */
@Service
public class AuthService {

    /**
     * Repository for user persistence operations.
     */
    private final UserRepository userRepository;

    /**
     * Mapper for converting User entities to response DTOs.
     */
    private final UserMapper userMapper;

    /**
     * Password encoder for secure password storage.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Utility for JWT token generation and validation.
     */
    private final JwtUtil jwtUtil;

    /**
     * Service for managing blacklisted tokens.
     */
    private final TokenBlackListService tokenBlackListService;

    /**
     * Service for managing active tokens.
     */
    private final TokenActiveList tokenActiveList;

    /**
     * Constructs an AuthService with required dependencies.
     *
     * @param userRepository         Repository for User persistence
     * @param userMapper             Mapper for User to DTOs
     * @param passwordEncoder        Password encoder
     * @param jwtUtil                JWT utility
     * @param tokenBlackListService  Service for blacklisted tokens
     * @param tokenActiveList        Service for active tokens
     */
    public AuthService(UserRepository userRepository, 
                      UserMapper userMapper, 
                      PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                      TokenBlackListService tokenBlackListService, TokenActiveList tokenActiveList) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenBlackListService = tokenBlackListService;
        this.tokenActiveList = tokenActiveList;
    }

    /**
     * Registers a new user with the provided registration request.
     * Validates password confirmation and checks for existing username/email.
     *
     * @param request AuthRegisterRequest containing registration details
     * @return AuthInternalResult with registration outcome
     * @throws IllegalArgumentException if passwords do not match or user/email exists
     */
    @Transactional
    public AuthInternalResult registerUser(AuthRegisterRequest request) {
        // Validate password confirmation
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        // Check if username or email already exists (single query)
        User existingUser = userRepository.findByUsernameOrEmail(
            request.getUsername(),
            request.getEmail()
        );
        if (existingUser != null) {
            if (existingUser.getUsername().equals(request.getUsername())) {
                throw new IllegalArgumentException("Username is already taken");
            } else {
                throw new IllegalArgumentException("Email is already in use");
            }
        }

        // Map request to User entity
        User user = userMapper.authRegisterRequestToUser(request);
        
        // Set password (encoded)
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        
        // Set default role and status
        user.setRole(User.roles.USER);
        user.setUserStatus(User.status.active);

        // Save user and get the saved instance
        User savedUser = userRepository.save(user);

        String accessToken = jwtUtil.generateAccessToken(savedUser.getId(), savedUser.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(savedUser.getId());


        String jti= jwtUtil.extractId(refreshToken);
        Instant exp = Instant.ofEpochMilli(jwtUtil.exctractExpireTime(refreshToken));
        Duration ttl = Duration.between(Instant.now(),exp );
        tokenActiveList.storeActiveRefreshToken(savedUser.getId(), jti, ttl);

        return new AuthInternalResult(savedUser.getId(), savedUser.getUsername(), accessToken, refreshToken); 
    }

    public AuthInternalResult login(AuthLoginRequest request){
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(
            () -> new IllegalArgumentException("Email or password is incorrect")
        );
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Email or password is incorrect");
        }
        
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        String jti= jwtUtil.extractId(refreshToken);
        Instant exp = Instant.ofEpochMilli(jwtUtil.exctractExpireTime(refreshToken));
        Duration ttl = Duration.between(Instant.now(),exp );
        tokenActiveList.storeActiveRefreshToken(user.getId(), jti, ttl);
        
        return new AuthInternalResult(user.getId(), user.getUsername(), accessToken, refreshToken); 

    }

    // Check username availability
    public boolean isUsernameAvailable(String username) {
        return !userRepository.findByUsername(username).isPresent();
    }

    // Check email availability
    public boolean isEmailAvailable(String email) {
        return !userRepository.findByEmail(email).isPresent();
    }




    public String refreshAccessToken(String activeRefreshToken) {
        
        activeRefreshToken = stripBearer(activeRefreshToken);

        if (activeRefreshToken == null || activeRefreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }
        // 1. Validate the refresh token
        if (!jwtUtil.validateRefreshToken(activeRefreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }
        if (tokenBlackListService.isTokenBlackListed(jwtUtil.extractId(activeRefreshToken))) {
            throw new IllegalArgumentException("Refresh token is revoked");
        }

        // 2. Extract user ID from refresh token
        Long userId = jwtUtil.extractUserId(activeRefreshToken);

        // 3. Get user from DB (to get current role, check if still active)
        User user = userRepository.findById(userId).orElseThrow(
            () -> new IllegalArgumentException("User not found")
        );

        if (user.getUserStatus() != User.status.active) {
            throw new IllegalArgumentException("User account is blocked");
        }

        return jwtUtil.generateAccessToken(userId, user.getRole().name());
    }

    public String refreshRefreshToken(String oldRefreshToken){
        oldRefreshToken = stripBearer(oldRefreshToken);

        if (oldRefreshToken == null || oldRefreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        if (!jwtUtil.validateRefreshToken(oldRefreshToken)) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        if (tokenBlackListService.isTokenBlackListed(jwtUtil.extractId(oldRefreshToken))) {
            throw new IllegalArgumentException("Refresh token is revoked");
        }

         Long userId = jwtUtil.extractUserId(oldRefreshToken);

        // 3. Get user from DB (to get current role, check if still active)
        User user = userRepository.findById(userId).orElseThrow(
            () -> new IllegalArgumentException("User not found")
        );

        if (user.getUserStatus() != User.status.active) {
            throw new IllegalArgumentException("User account is blocked");
        }

        String oldJti = jwtUtil.extractId(oldRefreshToken);
        
        Instant exp = Instant.ofEpochMilli(jwtUtil.exctractExpireTime(oldRefreshToken));
        Duration ttl = Duration.between(Instant.now(), exp);

        if (!tokenActiveList.isActiveRefreshToken(userId, oldJti)) {
            throw new IllegalArgumentException("Refresh token is not active");
        }
        if (!ttl.isNegative() && !ttl.isZero()) {
            tokenBlackListService.blacklist(oldJti, ttl);
        }

        String newRefreshtoken = jwtUtil.generateRefreshToken(userId);
        String newJti = jwtUtil.extractId(newRefreshtoken);
        Instant newExp = Instant.ofEpochMilli(jwtUtil.exctractExpireTime(newRefreshtoken));
        Duration newTtl = Duration.between(Instant.now(), newExp);
        tokenActiveList.removeActiveRefreshToken(userId);
        tokenActiveList.storeActiveRefreshToken(userId, newJti, newTtl);

        return newRefreshtoken;


    }

    public TokenPairResponse issueNewTokens(String oldRefreshToken) {
       String newRefreshToken = refreshRefreshToken(oldRefreshToken);
       String newAccessToken = refreshAccessToken(newRefreshToken);

       return new TokenPairResponse(newAccessToken, newRefreshToken);
    }


    public void logout(String accessToken, String refreshToken){
        boolean hasAccess = accessToken != null && !accessToken.isBlank();
        boolean hasRefresh = refreshToken != null && !refreshToken.isBlank();

        if (!hasAccess && !hasRefresh) {
            return; // nothing to revoke, best-effort logout
        }

        if (hasRefresh) {
            revokeRefreshToken(refreshToken);
        }

        if (hasAccess) {
            revokeAccessToken(accessToken);
        }

        Long userId = null;
        if (hasRefresh) {
            userId = safeExtractUserId(refreshToken);
        }
        if (userId == null && hasAccess) {
            userId = safeExtractUserId(accessToken);
        }

        if (userId != null) {
            tokenActiveList.removeActiveRefreshToken(userId);
        }
    }

    private void revokeRefreshToken(String refreshToken){
        String cleanToken = stripBearer(refreshToken);
        if (cleanToken == null || cleanToken.isBlank()) {
            return;
        }

        try {
            String jti = jwtUtil.extractId(cleanToken);
            if (jti == null || jti.isBlank() || tokenBlackListService.isTokenBlackListed(jti)) {
                return;
            }

            Instant exp = Instant.ofEpochMilli(jwtUtil.exctractExpireTime(cleanToken));
            Duration ttl = Duration.between(Instant.now(), exp);
            if (ttl.isNegative() || ttl.isZero()) {
                return;
            }

            tokenBlackListService.blacklist(jti, ttl);
        } catch (Exception ignored) {
            // Best-effort: ignore malformed/expired token
        }
    }

    private void revokeAccessToken(String accessToken){
        String cleanToken = stripBearer(accessToken);
        if (cleanToken == null || cleanToken.isBlank()) {
            return;
        }

        try {
            String jti = jwtUtil.extractId(cleanToken);
            if (jti == null || jti.isBlank() || tokenBlackListService.isTokenBlackListed(jti)) {
                return;
            }

            Instant exp = Instant.ofEpochMilli(jwtUtil.exctractExpireTime(cleanToken));
            Duration ttl = Duration.between(Instant.now(), exp);
            if (ttl.isNegative() || ttl.isZero()) {
                return;
            }

            tokenBlackListService.blacklist(jti, ttl);
        } catch (Exception ignored) {
            // Best-effort: ignore malformed/expired token
        }
    }

    private Long safeExtractUserId(String token){
        try {
            return jwtUtil.extractUserId(stripBearer(token));
        } catch (Exception e) {
            return null;
        }
    }

    private String stripBearer(String header){
        if (header==null || header.isBlank()) return null;

        String h = header.trim();

        return h.startsWith("Bearer ")?h.substring(7):h;
    }

}
