package com.example.auctionhub.auctionhub.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    // 32-byte key encoded in Base64 — satisfies HS256 minimum requirement
    private static final String TEST_SECRET =
        "dGVzdHNlY3JldGtleWZvcmF1Y3Rpb25odWJ0ZXN0aW5n";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET);
    }

    // -----------------------------------------------------------------------
    // generateAccessToken
    // -----------------------------------------------------------------------

    @Test
    void generateAccessToken_returnsNonNullToken() {
        assertNotNull(jwtUtil.generateAccessToken(1L, "USER"));
    }

    @Test
    void generateAccessToken_subjectIsUserId() {
        String token = jwtUtil.generateAccessToken(42L, "USER");
        Claims claims = jwtUtil.getClaims(token);
        assertEquals("42", claims.getSubject());
    }

    @Test
    void generateAccessToken_containsRoleClaim() {
        String token = jwtUtil.generateAccessToken(1L, "ADMIN");
        assertEquals("ADMIN", jwtUtil.getClaims(token).get("role", String.class));
    }

    @Test
    void generateAccessToken_typeClaimIsAccess() {
        String token = jwtUtil.generateAccessToken(1L, "USER");
        assertEquals("access", jwtUtil.getClaims(token).get("type", String.class));
    }

    @Test
    void generateAccessToken_audienceIsAccessAud() {
        String token = jwtUtil.generateAccessToken(1L, "USER");
        assertTrue(jwtUtil.getClaims(token).getAudience().contains("auctionhub-access"));
    }

    @Test
    void generateAccessToken_issuerIsAuctionhub() {
        String token = jwtUtil.generateAccessToken(1L, "USER");
        assertEquals("auctionhub", jwtUtil.getClaims(token).getIssuer());
    }

    @Test
    void generateAccessToken_hasUniqueJtiPerCall() {
        String token1 = jwtUtil.generateAccessToken(1L, "USER");
        String token2 = jwtUtil.generateAccessToken(1L, "USER");
        assertNotEquals(jwtUtil.getClaims(token1).getId(), jwtUtil.getClaims(token2).getId());
    }

    @Test
    void generateAccessToken_expiresInApproximately15Minutes() {
        long before = System.currentTimeMillis();
        String token = jwtUtil.generateAccessToken(1L, "USER");
        long expiry = jwtUtil.getClaims(token).getExpiration().getTime();

        long expectedExpiry = before + TimeUnit.MINUTES.toMillis(15);
        long toleranceMs = 2_000;

        assertTrue(Math.abs(expiry - expectedExpiry) < toleranceMs,
            "Expiry should be ~15 minutes from now");
    }

    // -----------------------------------------------------------------------
    // generateRefreshToken
    // -----------------------------------------------------------------------

    @Test
    void generateRefreshToken_returnsNonNullToken() {
        assertNotNull(jwtUtil.generateRefreshToken(1L));
    }

    @Test
    void generateRefreshToken_subjectIsUserId() {
        String token = jwtUtil.generateRefreshToken(99L);
        assertEquals("99", jwtUtil.getClaims(token).getSubject());
    }

    @Test
    void generateRefreshToken_typeClaimIsRefresh() {
        String token = jwtUtil.generateRefreshToken(1L);
        assertEquals("refresh", jwtUtil.getClaims(token).get("type", String.class));
    }

    @Test
    void generateRefreshToken_audienceIsRefreshAud() {
        String token = jwtUtil.generateRefreshToken(1L);
        assertTrue(jwtUtil.getClaims(token).getAudience().contains("auctionhub-refresh"));
    }

    @Test
    void generateRefreshToken_expiresInApproximately7Days() {
        long before = System.currentTimeMillis();
        String token = jwtUtil.generateRefreshToken(1L);
        long expiry = jwtUtil.getClaims(token).getExpiration().getTime();

        long expectedExpiry = before + TimeUnit.DAYS.toMillis(7);
        long toleranceMs = 2_000;

        assertTrue(Math.abs(expiry - expectedExpiry) < toleranceMs,
            "Expiry should be ~7 days from now");
    }

    @Test
    void generateRefreshToken_doesNotContainRoleClaim() {
        String token = jwtUtil.generateRefreshToken(1L);
        assertNull(jwtUtil.getClaims(token).get("role", String.class));
    }

    // -----------------------------------------------------------------------
    // validateAccessToken
    // -----------------------------------------------------------------------

    @Test
    void validateAccessToken_validAccessToken_returnsTrue() {
        String token = jwtUtil.generateAccessToken(1L, "USER");
        assertTrue(jwtUtil.validateAccessToken(token));
    }

    @Test
    void validateAccessToken_refreshTokenPassedIn_returnsFalse() {
        String refresh = jwtUtil.generateRefreshToken(1L);
        assertFalse(jwtUtil.validateAccessToken(refresh));
    }

    @Test
    void validateAccessToken_tamperedToken_returnsFalse() {
        String token = jwtUtil.generateAccessToken(1L, "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtUtil.validateAccessToken(tampered));
    }

    @Test
    void validateAccessToken_randomString_returnsFalse() {
        assertFalse(jwtUtil.validateAccessToken("not.a.jwt"));
    }

    @Test
    void validateAccessToken_emptyString_returnsFalse() {
        assertFalse(jwtUtil.validateAccessToken(""));
    }

    @Test
    void validateAccessToken_expiredToken_returnsFalse() {
        String expired = buildExpiredAccessToken();
        assertFalse(jwtUtil.validateAccessToken(expired));
    }

    // -----------------------------------------------------------------------
    // validateRefreshToken
    // -----------------------------------------------------------------------

    @Test
    void validateRefreshToken_validRefreshToken_returnsTrue() {
        String token = jwtUtil.generateRefreshToken(1L);
        assertTrue(jwtUtil.validateRefreshToken(token));
    }

    @Test
    void validateRefreshToken_accessTokenPassedIn_returnsFalse() {
        String access = jwtUtil.generateAccessToken(1L, "USER");
        assertFalse(jwtUtil.validateRefreshToken(access));
    }

    @Test
    void validateRefreshToken_tamperedToken_returnsFalse() {
        String token = jwtUtil.generateRefreshToken(1L);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtUtil.validateRefreshToken(tampered));
    }

    @Test
    void validateRefreshToken_expiredToken_returnsFalse() {
        String expired = buildExpiredRefreshToken();
        assertFalse(jwtUtil.validateRefreshToken(expired));
    }

    // -----------------------------------------------------------------------
    // Claim extractors
    // -----------------------------------------------------------------------

    @Test
    void extractUserId_returnsCorrectLong() {
        String token = jwtUtil.generateAccessToken(77L, "USER");
        assertEquals(77L, jwtUtil.extractUserId(token));
    }

    @Test
    void extractRole_returnsCorrectRole() {
        String token = jwtUtil.generateAccessToken(1L, "POSTER");
        assertEquals("POSTER", jwtUtil.extractRole(token));
    }

    @Test
    void extractId_returnsNonNullJti() {
        String token = jwtUtil.generateAccessToken(1L, "USER");
        assertNotNull(jwtUtil.extractId(token));
        assertFalse(jwtUtil.extractId(token).isBlank());
    }

    @Test
    void extractId_isValidUuidFormat() {
        String token = jwtUtil.generateAccessToken(1L, "USER");
        String jti = jwtUtil.extractId(token);
        assertDoesNotThrow(() -> UUID.fromString(jti), "JTI should be a valid UUID");
    }

    @Test
    void extractExpireTime_accessToken_isFutureTimestamp() {
        String token = jwtUtil.generateAccessToken(1L, "USER");
        long expiry = jwtUtil.exctractExpireTime(token);
        assertTrue(expiry > System.currentTimeMillis(), "Expiry must be in the future");
    }

    @Test
    void extractExpireTime_refreshToken_isFurterFutureThanAccessToken() {
        String access = jwtUtil.generateAccessToken(1L, "USER");
        String refresh = jwtUtil.generateRefreshToken(1L);
        assertTrue(jwtUtil.exctractExpireTime(refresh) > jwtUtil.exctractExpireTime(access),
            "Refresh token should expire later than access token");
    }

    // -----------------------------------------------------------------------
    // Helpers — build tokens outside of JwtUtil for edge case testing
    // -----------------------------------------------------------------------

    private String buildExpiredAccessToken() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        long past = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2);
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject("1")
            .issuer("auctionhub")
            .audience().add("auctionhub-access").and()
            .claim("type", "access")
            .claim("role", "USER")
            .issuedAt(new Date(past))
            .expiration(new Date(past + TimeUnit.MINUTES.toMillis(15)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    private String buildExpiredRefreshToken() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        long past = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8);
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject("1")
            .issuer("auctionhub")
            .audience().add("auctionhub-refresh").and()
            .claim("type", "refresh")
            .issuedAt(new Date(past))
            .expiration(new Date(past + TimeUnit.DAYS.toMillis(7)))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }
}
