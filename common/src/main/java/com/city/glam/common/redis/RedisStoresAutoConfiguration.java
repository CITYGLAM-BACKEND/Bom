package com.city.glam.common.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/** {@link RedisStores} for every service with a Redis connection (all services that depend on {@code common}). */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(LettuceConnectionFactory.class)
@EnableConfigurationProperties(RedisDatabases.class)
public class RedisStoresAutoConfiguration {

    @Bean
    @ConditionalOnBean(LettuceConnectionFactory.class)
    @ConditionalOnMissingBean
    RedisStores redisStores(LettuceConnectionFactory connectionFactory, RedisDatabases databases) {
        return new RedisStores(connectionFactory, databases);
    }
}
