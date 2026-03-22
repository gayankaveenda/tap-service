package au.com.transport.tapservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration.
 * Fare rules are loaded once from DB and cached in memory.
 * Cache expires after 1 hour — any fare changes take effect within an hour
 * without requiring an application restart.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String FARE_RULES_CACHE   = "fareRules";
    public static final String MAX_FARE_CACHE      = "maxFares";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
            FARE_RULES_CACHE,
            MAX_FARE_CACHE
        );
        manager.setCaffeine(
            Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats()              // enables cache hit/miss metrics via actuator
        );
        return manager;
    }
}
