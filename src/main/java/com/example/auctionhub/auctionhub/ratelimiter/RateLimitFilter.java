package com.example.auctionhub.auctionhub.ratelimiter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.auctionhub.auctionhub.constants.ApiConstants;

@Component
@Order(1)
public class RateLimitFilter implements Filter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String ip = request.getRemoteAddr();
        String path = ((HttpServletRequest) request).getRequestURI();
        Bandwidth limit;
        String bucketKey;
        //Bucket bucket = buckets.computeIfAbsent(ip, k -> Bucket.builder()
                //.addLimit(limit -> limit
                                //.capacity(100)
                                //.refillGreedy(100, Duration.ofMinutes(1)))
                //.build());

        if (path.startsWith(ApiConstants.AUTH_BASE)) {
            bucketKey = ip + ":auth";
            limit= Bandwidth.builder()
                    .capacity(10)
                    .refillGreedy(10, Duration.ofMinutes(30)).build();
        }
        else{
            bucketKey = ip + ":general";
            limit = Bandwidth.builder()
            .capacity(100)
            .refillGreedy(100, Duration.ofMinutes(30)).build();
        }

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k-> Bucket.builder().addLimit(limit).build());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(429);
            long nanosWait = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
            long secondsWait = nanosWait / 1_000_000_000;
            if (secondsWait >= 60) {
                
                long minutesWait = secondsWait / 60;
                httpResponse.setHeader("X-Rate-Limit-Retry-After-Minutes", String.valueOf(minutesWait));

            } else {
                
                long displaySeconds = Math.max(1, secondsWait); 
                httpResponse.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(displaySeconds));
            }
            httpResponse.getWriter().write("Too Many Requests");
        }
    }
}