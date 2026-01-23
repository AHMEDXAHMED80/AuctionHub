package com.example.auctionhub.auctionhub.redis;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TokenBlackListService {
    
    private static final String VALUE = "blacklisted";
    private static final String KEY_PREFIX = "auth:blacklist:";

    private final Logger log = LoggerFactory.getLogger(TokenBlackListService.class);

    private final StringRedisTemplate redisTemplate;

    public TokenBlackListService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklist(String jti, Duration ttl){
        String key = formatKey(jti);

        if (ttl == null || ttl.isZero() || ttl.isNegative()){
            throw new IllegalArgumentException("TTL not valid");
        }

        try {
            redisTemplate.opsForValue().set(key, VALUE, ttl);
           
        }catch(DataAccessException ex){
            log.error( "Failed to blacklist token {}",jti, ex);
            throw ex;
        }

        

    }

    private String formatKey(String jti){
        if (jti == null || jti.isBlank() ) {
            throw new IllegalArgumentException("JTI must be provided");
        }
        return KEY_PREFIX+ jti;
    }


    public Boolean isTokenBlackListed (String jti){
        String key = formatKey(jti);
        String value = redisTemplate.opsForValue().get(key);
        return VALUE.equals(value);
    }



}
