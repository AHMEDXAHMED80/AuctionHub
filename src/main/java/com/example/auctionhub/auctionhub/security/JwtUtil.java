package com.example.auctionhub.auctionhub.security;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {
    
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    
    private static final String ISSUER = "auctionhub";
  private static final long ACCESS_TTL_MS = TimeUnit.MINUTES.toMillis(15);
  private static final long REFRESH_TTL_MS = TimeUnit.DAYS.toMillis(7);
  private static final long CLOCK_SKEW_SEC = 60;
  private static final String ACCESS_AUD= "auctionhub-access";
  private static final String REFRESH_AUD = "auctionhub-refresh";

    private final SecretKey secretKey;
    private final JwtParser parser;

  public JwtUtil(@Value("${JWT_SECRET_B64}") String secret){
    secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    parser = Jwts.parser()
            .verifyWith((SecretKey)secretKey)
            .clockSkewSeconds(CLOCK_SKEW_SEC)
            .requireIssuer(ISSUER)
            .build();
    }

    public String generateAccessToken(Long userId, String role){
        Long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(ISSUER)
                .audience().add(ACCESS_AUD).and()
                .issuedAt(new Date(now))
                .claim("role", role)
                .claim("type", "access")
                .expiration(new Date(now + ACCESS_TTL_MS))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String generateRefreshToken(Long userId){
        Long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(ISSUER)
                .audience().add(REFRESH_AUD).and()
                .issuedAt(new Date(now))
                .notBefore(new Date(now-5_000))
                .claim("type", "refresh")
                .expiration(new Date(now + REFRESH_TTL_MS))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims getClaims(String token){
        return parser.parseSignedClaims(token).getPayload();
    }

    public boolean validateAccessToken(String token){
        try {
            Claims c = getClaims(token);
            if ("access".equals(c.get("type", String.class)) && c.getAudience().contains(ACCESS_AUD)) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Access token validation failed: {}", e.getMessage());
            return false;
        }
        return false;
    }

    public boolean validateRefreshToken(String token){
        try {
            Claims c = getClaims(token);
            if ("refresh".equals(c.get("type", String.class)) && c.getAudience().contains(REFRESH_AUD)) {
                return true;
            }
        } catch (Exception e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            return false;
        }
        return false;
    }

    public String extractId(String token){
        return getClaims(token).getId();
    }

    public String extractRole(String token){
        return getClaims(token).get("role", String.class);
    }

    public Long extractUserId(String token){
        return Long.parseLong(getClaims(token).getSubject());
    }

    public long exctractExpireTime(String token){
        return getClaims(token).getExpiration().getTime();
    }
}
