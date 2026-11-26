package com.city.glam.common.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cityglam.redis.databases.*}: which Redis database (index 0-15 on the one Redis server) holds which data.
 *
 * @param session  0: session values
 * @param uniqueId 1: unique ids (UUIDs and other one-time / unique values)
 * @param messages 2: response messages, keys {@code cityglam:messages:<screen>:<code>}
 */
@ConfigurationProperties(prefix = "cityglam.redis.databases")
public record RedisDatabases(
        @DefaultValue("0") int session,
        @DefaultValue("1") int uniqueId,
        @DefaultValue("2") int messages) {
}
