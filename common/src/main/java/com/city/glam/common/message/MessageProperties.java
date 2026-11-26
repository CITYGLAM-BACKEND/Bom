package com.city.glam.common.message;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code cityglam.messages.*}: response messages kept in Redis by Master-Data-Service and read by every service.
 *
 * @param enabled         read the texts from Redis (false: every message is the code itself)
 * @param keyPrefix       prefix of the Redis keys ({@link MessageKeys})
 * @param publishInterval Master-Data-Service only: how often the database is copied to Redis again, so database
 *                        edits reach every service after at most this long
 * @param schema          Master-Data-Service only: schema of the master tables
 * @param cacheTtl        how long a service keeps a message it read from Redis in memory (0 = read Redis every time)
 */
@ConfigurationProperties(prefix = "cityglam.messages")
public record MessageProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue(MessageKeys.DEFAULT_PREFIX) String keyPrefix,
        @DefaultValue("5m") Duration publishInterval,
        @DefaultValue("cityglam") String schema,
        @DefaultValue("1m") Duration cacheTtl) {
}
