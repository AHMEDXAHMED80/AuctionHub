package com.example.auctionhub.auctionhub.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private TokenBlackListService tokenBlackListService;
    @Mock private TokenActiveList tokenActiveList;

    @InjectMocks
    private AuthService authService;

    private User savedUser;
    private AuthRegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("testuser");
        savedUser.setEmail("test@example.com");
        savedUser.setRole(User.roles.USER);
        savedUser.setUserStatus(User.status.active);

        registerRequest = new AuthRegisterRequest(
            "John", "Doe", "testuser", "test@example.com",
            LocalDate.of(1990, 1, 1), "Password1!", User.roles.USER, "Password1!"
        );
    }

    // ==================== registerUser ====================

    @Test
    void register_success_returnsAuthResult() {
        when(userRepository.findByUsernameOrEmail("testuser", "test@example.com")).thenReturn(null);
        when(userMapper.authRegisterRequestToUser(any())).thenReturn(new User());
        when(passwordEncoder.encode("Password1!")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtil.generateAccessToken(1L, "USER")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtUtil.extractId("refresh-token")).thenReturn("jti-1");
        when(jwtUtil.exctractExpireTime("refresh-token")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());

        AuthInternalResult result = authService.registerUser(registerRequest);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void register_passwordsMismatch_throws() {
        registerRequest.setConfirmPassword("WrongPassword1!");

        assertThatThrownBy(() -> authService.registerUser(registerRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Passwords do not match");

        verifyNoInteractions(userRepository, userMapper, passwordEncoder, jwtUtil);
    }

    @Test
    void register_usernameTaken_throws() {
        User existing = new User();
        existing.setUsername("testuser");
        when(userRepository.findByUsernameOrEmail("testuser", "test@example.com")).thenReturn(existing);

        assertThatThrownBy(() -> authService.registerUser(registerRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Username is already taken");
    }

    @Test
    void register_emailTaken_throws() {
        User existing = new User();
        existing.setUsername("different"); // different username → falls to email check
        when(userRepository.findByUsernameOrEmail("testuser", "test@example.com")).thenReturn(existing);

        assertThatThrownBy(() -> authService.registerUser(registerRequest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email is already in use");
    }

    @Test
    void register_encodesPassword() {
        User mappedUser = new User();
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(null);
        when(userMapper.authRegisterRequestToUser(any())).thenReturn(mappedUser);
        when(passwordEncoder.encode("Password1!")).thenReturn("hashed-pw");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtil.generateAccessToken(anyLong(), anyString())).thenReturn("at");
        when(jwtUtil.generateRefreshToken(anyLong())).thenReturn("rt");
        when(jwtUtil.extractId("rt")).thenReturn("jti");
        when(jwtUtil.exctractExpireTime("rt")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());

        authService.registerUser(registerRequest);

        // Password must be encoded before save
        assertThat(mappedUser.getPassword()).isEqualTo("hashed-pw");
    }

    @Test
    void register_setsDefaultRoleAndStatus() {
        User mappedUser = new User();
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(null);
        when(userMapper.authRegisterRequestToUser(any())).thenReturn(mappedUser);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtil.generateAccessToken(anyLong(), anyString())).thenReturn("at");
        when(jwtUtil.generateRefreshToken(anyLong())).thenReturn("rt");
        when(jwtUtil.extractId("rt")).thenReturn("jti");
        when(jwtUtil.exctractExpireTime("rt")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());

        authService.registerUser(registerRequest);

        // Role and status are always forced — never taken from request
        assertThat(mappedUser.getRole()).isEqualTo(User.roles.USER);
        assertThat(mappedUser.getUserStatus()).isEqualTo(User.status.active);
    }

    @Test
    void register_storesActiveRefreshToken() {
        when(userRepository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(null);
        when(userMapper.authRegisterRequestToUser(any())).thenReturn(new User());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtUtil.generateAccessToken(anyLong(), anyString())).thenReturn("at");
        when(jwtUtil.generateRefreshToken(1L)).thenReturn("rt");
        when(jwtUtil.extractId("rt")).thenReturn("jti-123");
        when(jwtUtil.exctractExpireTime("rt")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());

        authService.registerUser(registerRequest);

        verify(tokenActiveList).storeActiveRefreshToken(eq(1L), eq("jti-123"), any());
    }

    // ==================== login ====================

    @Test
    void login_success_returnsAuthResult() {
        AuthLoginRequest req = new AuthLoginRequest("test@example.com", "Password1!");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("Password1!", savedUser.getPassword())).thenReturn(true);
        when(jwtUtil.generateAccessToken(1L, "USER")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtUtil.extractId("refresh-token")).thenReturn("jti-1");
        when(jwtUtil.exctractExpireTime("refresh-token")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());

        AuthInternalResult result = authService.login(req);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void login_emailNotFound_throws() {
        AuthLoginRequest req = new AuthLoginRequest("wrong@example.com", "Password1!");
        when(userRepository.findByEmail("wrong@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email or password is incorrect");
    }

    @Test
    void login_wrongPassword_throws() {
        AuthLoginRequest req = new AuthLoginRequest("test@example.com", "WrongPass!");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("WrongPass!", savedUser.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Email or password is incorrect");
    }

    @Test
    void login_storesActiveRefreshToken() {
        AuthLoginRequest req = new AuthLoginRequest("test@example.com", "Password1!");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);
        when(jwtUtil.generateAccessToken(anyLong(), anyString())).thenReturn("at");
        when(jwtUtil.generateRefreshToken(1L)).thenReturn("rt");
        when(jwtUtil.extractId("rt")).thenReturn("jti-456");
        when(jwtUtil.exctractExpireTime("rt")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());

        authService.login(req);

        verify(tokenActiveList).storeActiveRefreshToken(eq(1L), eq("jti-456"), any());
    }

    // ==================== isUsernameAvailable ====================

    @Test
    void isUsernameAvailable_notInDb_returnsTrue() {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        assertThat(authService.isUsernameAvailable("newuser")).isTrue();
    }

    @Test
    void isUsernameAvailable_inDb_returnsFalse() {
        when(userRepository.findByUsername("taken")).thenReturn(Optional.of(savedUser));
        assertThat(authService.isUsernameAvailable("taken")).isFalse();
    }

    // ==================== isEmailAvailable ====================

    @Test
    void isEmailAvailable_notInDb_returnsTrue() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        assertThat(authService.isEmailAvailable("new@example.com")).isTrue();
    }

    @Test
    void isEmailAvailable_inDb_returnsFalse() {
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(savedUser));
        assertThat(authService.isEmailAvailable("taken@example.com")).isFalse();
    }

    // ==================== refreshAccessToken ====================

    @Test
    void refreshAccessToken_nullToken_throws() {
        assertThatThrownBy(() -> authService.refreshAccessToken(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Refresh token is required");
    }

    @Test
    void refreshAccessToken_blankToken_throws() {
        assertThatThrownBy(() -> authService.refreshAccessToken("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Refresh token is required");
    }

    @Test
    void refreshAccessToken_invalidToken_throws() {
        when(jwtUtil.validateRefreshToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshAccessToken("bad-token"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid or expired refresh token");
    }

    @Test
    void refreshAccessToken_blacklistedToken_throws() {
        when(jwtUtil.validateRefreshToken("rt")).thenReturn(true);
        when(jwtUtil.extractId("rt")).thenReturn("jti-1");
        when(tokenBlackListService.isTokenBlackListed("jti-1")).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshAccessToken("rt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Refresh token is revoked");
    }

    @Test
    void refreshAccessToken_userNotFound_throws() {
        when(jwtUtil.validateRefreshToken("rt")).thenReturn(true);
        when(jwtUtil.extractId("rt")).thenReturn("jti-1");
        when(tokenBlackListService.isTokenBlackListed("jti-1")).thenReturn(false);
        when(jwtUtil.extractUserId("rt")).thenReturn(99L);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshAccessToken("rt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("User not found");
    }

    @Test
    void refreshAccessToken_userBlocked_throws() {
        savedUser.setUserStatus(User.status.blocked);
        when(jwtUtil.validateRefreshToken("rt")).thenReturn(true);
        when(jwtUtil.extractId("rt")).thenReturn("jti-1");
        when(tokenBlackListService.isTokenBlackListed("jti-1")).thenReturn(false);
        when(jwtUtil.extractUserId("rt")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));

        assertThatThrownBy(() -> authService.refreshAccessToken("rt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("User account is blocked");
    }

    @Test
    void refreshAccessToken_success_returnsNewAccessToken() {
        when(jwtUtil.validateRefreshToken("rt")).thenReturn(true);
        when(jwtUtil.extractId("rt")).thenReturn("jti-1");
        when(tokenBlackListService.isTokenBlackListed("jti-1")).thenReturn(false);
        when(jwtUtil.extractUserId("rt")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(jwtUtil.generateAccessToken(1L, "USER")).thenReturn("new-access-token");

        String result = authService.refreshAccessToken("rt");

        assertThat(result).isEqualTo("new-access-token");
    }

    @Test
    void refreshAccessToken_stripsBearer_success() {
        // Token is passed with "Bearer " prefix — should be stripped before validation
        when(jwtUtil.validateRefreshToken("rt")).thenReturn(true);
        when(jwtUtil.extractId("rt")).thenReturn("jti-1");
        when(tokenBlackListService.isTokenBlackListed("jti-1")).thenReturn(false);
        when(jwtUtil.extractUserId("rt")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(jwtUtil.generateAccessToken(1L, "USER")).thenReturn("new-access-token");

        String result = authService.refreshAccessToken("Bearer rt");

        assertThat(result).isEqualTo("new-access-token");
    }

    // ==================== refreshRefreshToken ====================

    @Test
    void refreshRefreshToken_nullToken_throws() {
        assertThatThrownBy(() -> authService.refreshRefreshToken(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Refresh token is required");
    }

    @Test
    void refreshRefreshToken_invalidToken_throws() {
        when(jwtUtil.validateRefreshToken("old-rt")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshRefreshToken("old-rt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid or expired refresh token");
    }

    @Test
    void refreshRefreshToken_blacklistedToken_throws() {
        when(jwtUtil.validateRefreshToken("old-rt")).thenReturn(true);
        when(jwtUtil.extractId("old-rt")).thenReturn("old-jti");
        when(tokenBlackListService.isTokenBlackListed("old-jti")).thenReturn(true);

        assertThatThrownBy(() -> authService.refreshRefreshToken("old-rt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Refresh token is revoked");
    }

    @Test
    void refreshRefreshToken_userBlocked_throws() {
        savedUser.setUserStatus(User.status.blocked);
        when(jwtUtil.validateRefreshToken("old-rt")).thenReturn(true);
        when(jwtUtil.extractId("old-rt")).thenReturn("old-jti");
        when(tokenBlackListService.isTokenBlackListed("old-jti")).thenReturn(false);
        when(jwtUtil.extractUserId("old-rt")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));

        assertThatThrownBy(() -> authService.refreshRefreshToken("old-rt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("User account is blocked");
    }

    @Test
    void refreshRefreshToken_tokenNotActive_throws() {
        when(jwtUtil.validateRefreshToken("old-rt")).thenReturn(true);
        when(jwtUtil.extractId("old-rt")).thenReturn("old-jti");
        when(tokenBlackListService.isTokenBlackListed("old-jti")).thenReturn(false);
        when(jwtUtil.extractUserId("old-rt")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(jwtUtil.exctractExpireTime("old-rt")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());
        when(tokenActiveList.isActiveRefreshToken(1L, "old-jti")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshRefreshToken("old-rt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Refresh token is not active");
    }

    @Test
    void refreshRefreshToken_success_blacklistsOldAndStoresNew() {
        when(jwtUtil.validateRefreshToken("old-rt")).thenReturn(true);
        when(jwtUtil.extractId("old-rt")).thenReturn("old-jti");
        when(tokenBlackListService.isTokenBlackListed("old-jti")).thenReturn(false);
        when(jwtUtil.extractUserId("old-rt")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(jwtUtil.exctractExpireTime("old-rt")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());
        when(tokenActiveList.isActiveRefreshToken(1L, "old-jti")).thenReturn(true);
        when(jwtUtil.generateRefreshToken(1L)).thenReturn("new-rt");
        when(jwtUtil.extractId("new-rt")).thenReturn("new-jti");
        when(jwtUtil.exctractExpireTime("new-rt")).thenReturn(Instant.now().plusSeconds(604800).toEpochMilli());

        String result = authService.refreshRefreshToken("old-rt");

        assertThat(result).isEqualTo("new-rt");
        verify(tokenBlackListService).blacklist(eq("old-jti"), any());
        verify(tokenActiveList).removeActiveRefreshToken(1L);
        verify(tokenActiveList).storeActiveRefreshToken(eq(1L), eq("new-jti"), any());
    }

    // ==================== issueNewTokens ====================

    @Test
    void issueNewTokens_success_returnsBothTokens() {
        // refreshRefreshToken stubs
        when(jwtUtil.validateRefreshToken("old-rt")).thenReturn(true);
        when(jwtUtil.extractId("old-rt")).thenReturn("old-jti");
        when(tokenBlackListService.isTokenBlackListed("old-jti")).thenReturn(false);
        when(jwtUtil.extractUserId("old-rt")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
        when(jwtUtil.exctractExpireTime("old-rt")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());
        when(tokenActiveList.isActiveRefreshToken(1L, "old-jti")).thenReturn(true);
        when(jwtUtil.generateRefreshToken(1L)).thenReturn("new-rt");
        when(jwtUtil.extractId("new-rt")).thenReturn("new-jti");
        when(jwtUtil.exctractExpireTime("new-rt")).thenReturn(Instant.now().plusSeconds(604800).toEpochMilli());

        // refreshAccessToken stubs (called with "new-rt")
        when(jwtUtil.validateRefreshToken("new-rt")).thenReturn(true);
        when(tokenBlackListService.isTokenBlackListed("new-jti")).thenReturn(false);
        when(jwtUtil.extractUserId("new-rt")).thenReturn(1L);
        when(jwtUtil.generateAccessToken(1L, "USER")).thenReturn("new-at");

        TokenPairResponse result = authService.issueNewTokens("old-rt");

        assertThat(result.getAccessToken()).isEqualTo("new-at");
        assertThat(result.getRefreshToken()).isEqualTo("new-rt");
    }

    // ==================== logout ====================

    @Test
    void logout_bothNullOrBlank_isNoOp() {
        authService.logout(null, null);
        authService.logout("", "  ");

        verifyNoInteractions(jwtUtil, tokenBlackListService, tokenActiveList);
    }

    @Test
    void logout_bothTokens_revokesAll() {
        when(jwtUtil.extractId("rt")).thenReturn("jti-r");
        when(tokenBlackListService.isTokenBlackListed("jti-r")).thenReturn(false);
        when(jwtUtil.exctractExpireTime("rt")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());

        when(jwtUtil.extractId("at")).thenReturn("jti-a");
        when(tokenBlackListService.isTokenBlackListed("jti-a")).thenReturn(false);
        when(jwtUtil.exctractExpireTime("at")).thenReturn(Instant.now().plusSeconds(900).toEpochMilli());

        when(jwtUtil.extractUserId("rt")).thenReturn(1L);

        authService.logout("at", "rt");

        verify(tokenBlackListService).blacklist(eq("jti-r"), any());
        verify(tokenBlackListService).blacklist(eq("jti-a"), any());
        verify(tokenActiveList).removeActiveRefreshToken(1L);
    }

    @Test
    void logout_onlyRefreshToken_revokesRefreshAndRemovesActive() {
        when(jwtUtil.extractId("rt")).thenReturn("jti-r");
        when(tokenBlackListService.isTokenBlackListed("jti-r")).thenReturn(false);
        when(jwtUtil.exctractExpireTime("rt")).thenReturn(Instant.now().plusSeconds(3600).toEpochMilli());
        when(jwtUtil.extractUserId("rt")).thenReturn(1L);

        authService.logout(null, "rt");

        verify(tokenBlackListService).blacklist(eq("jti-r"), any());
        verify(tokenActiveList).removeActiveRefreshToken(1L);
        verify(tokenBlackListService, times(1)).blacklist(any(), any()); // only refresh blacklisted
    }

    @Test
    void logout_alreadyBlacklistedRefreshToken_skipsBlacklisting() {
        when(jwtUtil.extractId("rt")).thenReturn("jti-r");
        when(tokenBlackListService.isTokenBlackListed("jti-r")).thenReturn(true); // already blacklisted

        when(jwtUtil.extractId("at")).thenReturn("jti-a");
        when(tokenBlackListService.isTokenBlackListed("jti-a")).thenReturn(false);
        when(jwtUtil.exctractExpireTime("at")).thenReturn(Instant.now().plusSeconds(900).toEpochMilli());

        when(jwtUtil.extractUserId(anyString())).thenReturn(1L);

        authService.logout("at", "rt");

        // Refresh token already blacklisted — skip it; access token still revoked
        verify(tokenBlackListService, never()).blacklist(eq("jti-r"), any());
        verify(tokenBlackListService).blacklist(eq("jti-a"), any());
    }

    @Test
    void logout_expiredToken_skipsBlacklisting() {
        // If a token is already expired (TTL <= 0), no need to blacklist it
        when(jwtUtil.extractId("rt")).thenReturn("jti-r");
        when(tokenBlackListService.isTokenBlackListed("jti-r")).thenReturn(false);
        when(jwtUtil.exctractExpireTime("rt")).thenReturn(Instant.now().minusSeconds(10).toEpochMilli()); // expired

        when(jwtUtil.extractUserId("rt")).thenReturn(1L);

        authService.logout(null, "rt");

        verify(tokenBlackListService, never()).blacklist(any(), any());
        verify(tokenActiveList).removeActiveRefreshToken(1L);
    }
}
