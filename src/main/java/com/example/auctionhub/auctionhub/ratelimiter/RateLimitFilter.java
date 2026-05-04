package com.example.auctionhub.auctionhub.ratelimiter;

// Bucket4j imports
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

// Jakarta Servlet imports
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Java standard imports
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Spring imports
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

// Your constants
import com.example.auctionhub.auctionhub.constants.ApiConstants;

@Component
@Order(1)
public class RateLimitFilter implements Filter {
    
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();
    
    // Cleanup configuration
    private static final long CLEANUP_INTERVAL_MS = 60_000;      // 1 minute
    private static final long BUCKET_EXPIRY_MS = 1_800_000;      // 30 minutes
    private long lastCleanup = System.currentTimeMillis();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        // Clean up old buckets periodically
        cleanupOldBuckets();
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String ip = getClientIp(httpRequest);
        String path = httpRequest.getRequestURI();
        
        Bandwidth limit;
        String bucketKey;

        // Different limits for different endpoints
        if (path.startsWith(ApiConstants.AUTH_BASE)) {
            bucketKey = ip + ":auth";
            limit = Bandwidth.builder()
                    .capacity(5)
                    .refillGreedy(5, Duration.ofMinutes(1))     // 5 per 1 minute
                    .build();
        }
        else if (path.startsWith("/api/items/search")) {
            bucketKey = ip + ":search";
            limit = Bandwidth.builder()
                    .capacity(30)
                    .refillGreedy(30, Duration.ofMinutes(1))    // 30 per 1 minute
                    .build();
        }
        else {
            bucketKey = ip + ":general";
            limit = Bandwidth.builder()
                    .capacity(100)
                    .refillGreedy(100, Duration.ofMinutes(1))   // 100 per 1 minute
                    .build();
        }

        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> Bucket.builder().addLimit(limit).build());
        lastAccess.put(bucketKey, System.currentTimeMillis());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            
            long nanosWait = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
            long secondsWait = nanosWait / 1_000_000_000;
            String retryAfter;
            
            if (secondsWait >= 60) {
                long minutesWait = secondsWait / 60;
                httpResponse.setHeader("X-Rate-Limit-Retry-After-Minutes", String.valueOf(minutesWait));
                retryAfter = minutesWait + " minute" + (minutesWait > 1 ? "s" : "");
            } else {
                long displaySeconds = Math.max(1, secondsWait);
                httpResponse.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(displaySeconds));
                retryAfter = displaySeconds + " second" + (displaySeconds > 1 ? "s" : "");
            }
            
            httpResponse.getWriter().write(
                    "{\"error\":\"Too many requests. Please try again after " + retryAfter + ".\"}");
        }
    }

    /**
     * Get client IP address, handling proxies (X-Forwarded-For, X-Real-IP)
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Clean up old buckets to prevent memory leak
     */
    private void cleanupOldBuckets() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            lastAccess.entrySet().removeIf(entry -> {
                if (now - entry.getValue() > BUCKET_EXPIRY_MS) {
                    buckets.remove(entry.getKey());
                    return true;
                }
                return false;
            });
            lastCleanup = now;
        }
    }
}
