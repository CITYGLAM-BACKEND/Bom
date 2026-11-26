package com.city.glam.common.message;

import com.city.glam.common.redis.RedisStores;
import com.city.glam.common.redis.RedisStoresAutoConfiguration;
import com.city.glam.common.web.ApiResponses;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picked up automatically by every service that depends on {@code common}: a {@link MessageReader} that reads the
 * response messages from Redis database 2 ({@link RedisStores#messages()}), and the {@link ApiResponses} builder that uses it. Without Redis every message is
 * the code itself. Writing to Redis is done only by Master-Data-Service ({@link RedisMessagePublisher}).
 */
@AutoConfiguration(after = RedisStoresAutoConfiguration.class)
@EnableConfigurationProperties(MessageProperties.class)
public class MessagesAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(RedisStores.class)
    @ConditionalOnProperty(prefix = "cityglam.messages", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class RedisMessages {

        @Bean
        @ConditionalOnMissingBean
        MessageReader messageReader(RedisStores redis, MessageProperties properties) {
            return new RedisMessageReader(redis.messages(), properties.keyPrefix(), properties.cacheTtl());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletResponses {

        @Bean
        @ConditionalOnMissingBean
        ApiResponses apiResponses(ObjectProvider<MessageReader> reader) {
            return new ApiResponses(reader.getIfAvailable(() -> MessageReader.CODES_ONLY));
        }
    }
}
