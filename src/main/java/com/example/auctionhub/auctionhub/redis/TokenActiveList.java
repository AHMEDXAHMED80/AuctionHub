package com.example.auctionhub.auctionhub.redis;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenActiveList {
    private static final String KEY_PREFIX = "auth:active:";

    private final StringRedisTemplate redisTemplate;

    public TokenActiveList(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    private String formatKey(Long userId){
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        return KEY_PREFIX + userId;
    }

    public void storeActiveRefreshToken(Long userId, String jti, Duration ttl){
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti must not be null or blank");
        }
        String key = formatKey(userId);
        redisTemplate.opsForValue().set(key, jti, ttl);
    }

    public boolean isActiveRefreshToken(Long userId, String jti){
        String key = formatKey(userId);
        String activeJti = redisTemplate.opsForValue().get(key);
        return jti.equals(activeJti);
    }

    public void removeActiveRefreshToken(Long userId){
        String key = formatKey(userId);
        redisTemplate.delete(key);
    
        
    }

}