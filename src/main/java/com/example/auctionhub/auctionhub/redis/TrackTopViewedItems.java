package com.example.auctionhub.auctionhub.redis;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TrackTopViewedItems {
    private static final Logger logger = LoggerFactory.getLogger(TrackTopViewedItems.class);
    private final StringRedisTemplate topVieweditemsredisTemplate;
    private final String VIEWS_KEY = "items:views";


    public TrackTopViewedItems(StringRedisTemplate topVieweditemsredisTemplate) {
        this.topVieweditemsredisTemplate = topVieweditemsredisTemplate;
    }

    public void trackItemView(Long itemId) {
        try {
            if (itemId == null) {
                logger.error("itemId must not be null");
                throw new IllegalArgumentException("itemId must not be null");
            }

            topVieweditemsredisTemplate.opsForZSet().incrementScore(VIEWS_KEY, itemId.toString(), 1);
            logger.debug("Tracked view for item {}", itemId);

        } catch (DataAccessException ex) {
            logger.error("Failed to track view for item {}", itemId, ex);
            throw ex;
        }
    }

    public Set<String> getTopFiveViewedItems() {
        try {
            Set<String> topItems = topVieweditemsredisTemplate.opsForZSet()
                .reverseRange(VIEWS_KEY, 0, 4);

            logger.debug("Retrieved top 5 viewed items: {}", topItems);
            return topItems;

        } catch (Exception e) {
            logger.error("Failed to retrieve top 5 viewed items", e);
            throw new RuntimeException("Failed to retrieve top viewed items", e);
        }
    }
}
