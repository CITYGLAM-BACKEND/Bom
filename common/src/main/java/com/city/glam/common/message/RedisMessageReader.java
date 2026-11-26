package com.city.glam.common.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Reads response messages from Redis, where Master-Data-Service keeps them ({@link MessageKeys}). One {@code HGETALL}
 * for the request's screen, a second one for COMMON when the screen does not have the code.
 * <ul>
 *   <li>Every hash found is kept in memory for {@code cityglam.messages.cache-ttl} (default 1 minute), so Redis is
 *       NOT on the path of every API response; an edit published by Master-Data is visible within that time.</li>
 *   <li>When Redis cannot be reached, the last copy is used even if older; without one the message is the code itself
 *       (the request never fails because of a message).</li>
 *   <li>Only hashes that exist are remembered, and at most {@value #MAX_ENTRIES} of them: the screen comes from client
 *       headers, so made-up screens must not fill the memory.</li>
 * </ul>
 */
public class RedisMessageReader implements MessageReader {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageReader.class);
    private static final long WARN_EVERY_MILLIS = 60_000;
    static final int MAX_ENTRIES = 10_000;

    private record Cached(Map<String, String> hash, long expiresAt) {
    }

    private final Function<String, Map<String, String>> hashes;
    private final String prefix;
    private final long ttlMillis;
    private final LongSupplier clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private volatile long lastWarning;

    public RedisMessageReader(StringRedisTemplate redis, String prefix, Duration cacheTtl) {
        this(key -> {
            Map<String, String> hash = new HashMap<>();
            redis.<String, String>opsForHash().entries(key).forEach(hash::put);
            return hash;
        }, prefix, cacheTtl, System::currentTimeMillis);
    }

    /** Without the in-memory copy (every lookup goes to Redis). */
    public RedisMessageReader(StringRedisTemplate redis, String prefix) {
        this(redis, prefix, Duration.ZERO);
    }

    /** For tests: any key -> hash lookup, no in-memory copy. */
    public RedisMessageReader(Function<String, Map<String, String>> hashes, String prefix) {
        this(hashes, prefix, Duration.ZERO, System::currentTimeMillis);
    }

    /** For tests: any key -> hash lookup, with an in-memory copy and a clock. */
    public RedisMessageReader(Function<String, Map<String, String>> hashes, String prefix, Duration cacheTtl, LongSupplier clock) {
        this.hashes = hashes;
        this.prefix = prefix;
        this.ttlMillis = cacheTtl == null ? 0 : cacheTtl.toMillis();
        this.clock = clock;
    }

    @Override
    public ResolvedMessage resolve(String code, Screen screen, Locale locale, Object... args) {
        List<Screen> candidates = screen == null || screen.equals(Screen.COMMON) ? List.of(Screen.COMMON) : List.of(screen, Screen.COMMON);
        try {
            for (Screen candidate : candidates) {
                Map<String, String> hash = lookup(MessageKeys.key(prefix, candidate, code));
                if (hash != null && !hash.isEmpty()) {
                    return MessageTexts.resolve(code, hash, locale, candidate.path(), args);
                }
            }
            return new ResolvedMessage(code, null, "no message key");
        } catch (RuntimeException e) {
            long now = System.currentTimeMillis();
            if (now - lastWarning > WARN_EVERY_MILLIS) {
                lastWarning = now;
                log.warn("Response messages: Redis unavailable, answering with codes only: {}", e.getMessage());
            }
            return new ResolvedMessage(code, null, "redis unavailable");
        }
    }

    private Map<String, String> lookup(String key) {
        if (ttlMillis <= 0) {
            return hashes.apply(key);
        }
        long now = clock.getAsLong();
        Cached cached = cache.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.hash();
        }
        Map<String, String> fresh;
        try {
            fresh = hashes.apply(key);
        } catch (RuntimeException e) {
            if (cached != null) {
                return cached.hash();   // Redis down: an older copy beats a bare code
            }
            throw e;
        }
        if (fresh != null && !fresh.isEmpty()) {
            if (cache.size() >= MAX_ENTRIES) {
                cache.clear();
            }
            cache.put(key, new Cached(Map.copyOf(fresh), now + ttlMillis));
        } else {
            cache.remove(key);
        }
        return fresh;
    }
}
