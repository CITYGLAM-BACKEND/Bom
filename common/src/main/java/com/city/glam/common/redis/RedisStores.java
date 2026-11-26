package com.city.glam.common.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The three Redis databases of City Glam, on the one Redis server ({@code spring.data.redis.*}: host, password,
 * timeouts are shared):
 * <ul>
 *     <li>{@link #session()}  database 0: session values</li>
 *     <li>{@link #uniqueIds()} database 1: unique ids (UUIDs and other unique values)</li>
 *     <li>{@link #messages()} database 2: response messages ({@code cityglam:messages:<screen>:<code>})</li>
 * </ul>
 * Inject this bean and pick the database: {@code stores.uniqueIds().opsForValue().setIfAbsent(...)}. The numbers can be
 * changed with {@code cityglam.redis.databases.*}. Spring's default {@code StringRedisTemplate} stays on
 * {@code spring.data.redis.database} (0), which the gateway's rate limiter uses.
 */
public class RedisStores implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(RedisStores.class);

    private final StringRedisTemplate session;
    private final StringRedisTemplate uniqueIds;
    private final StringRedisTemplate messages;
    private final List<LettuceConnectionFactory> ownFactories = new ArrayList<>();

    public RedisStores(LettuceConnectionFactory base, RedisDatabases databases) {
        this.session = template(base, databases.session());
        this.uniqueIds = template(base, databases.uniqueId());
        this.messages = template(base, databases.messages());
    }

    /** Database 0: session values. */
    public StringRedisTemplate session() {
        return session;
    }

    /** Database 1: unique ids (UUIDs and other unique values). */
    public StringRedisTemplate uniqueIds() {
        return uniqueIds;
    }

    /** Database 2: response messages. */
    public StringRedisTemplate messages() {
        return messages;
    }

    /** Same server, client settings and password as the application's connection; only the database differs. */
    private StringRedisTemplate template(LettuceConnectionFactory base, int database) {
        if (database == base.getDatabase()) {
            return new StringRedisTemplate(base);
        }
        if (base.isClusterAware() || base.isRedisSentinelAware()) {
            // Redis Cluster has only database 0; keep everything on the application's connection
            log.warn("Redis cluster/sentinel connection: database {} not selectable, using database {}", database, base.getDatabase());
            return new StringRedisTemplate(base);
        }
        RedisStandaloneConfiguration source = base.getStandaloneConfiguration();
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(source.getHostName(), source.getPort());
        config.setUsername(source.getUsername());
        config.setPassword(source.getPassword());
        config.setDatabase(database);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, base.getClientConfiguration());
        factory.afterPropertiesSet();
        factory.start();
        ownFactories.add(factory);
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    @Override
    public void destroy() {
        ownFactories.forEach(LettuceConnectionFactory::destroy);
    }
}
