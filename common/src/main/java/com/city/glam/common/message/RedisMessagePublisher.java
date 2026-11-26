package com.city.glam.common.message;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes the response messages to Redis database 2 ({@link MessageKeys}, {@code RedisStores.messages()}). Used only by Master-Data-Service, which owns the
 * message tables. Each hash is updated in place (new fields written first, removed fields deleted after), so readers
 * never see a missing key while a publish runs; keys of rows that no longer exist are deleted.
 */
public class RedisMessagePublisher {

    private final StringRedisTemplate redis;
    private final String prefix;

    public RedisMessagePublisher(StringRedisTemplate redis, String prefix) {
        this.redis = redis;
        this.prefix = prefix;
    }

    /**
     * Two pipelined round trips for ALL keys (read the current field names, then write and remove), instead of three
     * round trips per key: a few thousand messages are published in milliseconds by every Master-Data replica.
     *
     * @return the number of message keys now in Redis
     */
    public int publish(Collection<MessageRow> rows, String defaultLanguage) {
        Map<String, Map<String, String>> hashes = toHashes(rows, defaultLanguage);
        List<String> keys = new ArrayList<>(hashes.keySet());
        // Raw connection commands (UTF-8 bytes): the pipelined connection is a proxy, not a StringRedisConnection
        List<Object> current = keys.isEmpty() ? List.of() : redis.executePipelined((RedisCallback<Object>) connection -> {
            keys.forEach(key -> connection.hashCommands().hKeys(bytes(key)));
            return null;
        });
        if (!keys.isEmpty()) {
            redis.executePipelined((RedisCallback<Object>) connection -> {
                for (int i = 0; i < keys.size(); i++) {
                    String key = keys.get(i);
                    Map<String, String> fields = hashes.get(key);
                    Map<byte[], byte[]> raw = new LinkedHashMap<>();
                    fields.forEach((field, value) -> raw.put(bytes(field), bytes(value)));
                    connection.hashCommands().hMSet(bytes(key), raw);
                    Set<String> stale = new HashSet<>();
                    if (current.get(i) instanceof Collection<?> existing) {
                        existing.forEach(field -> stale.add(String.valueOf(field)));
                    }
                    stale.removeAll(fields.keySet());
                    if (!stale.isEmpty()) {
                        connection.hashCommands().hDel(bytes(key), stale.stream().map(RedisMessagePublisher::bytes).toArray(byte[][]::new));
                    }
                }
                return null;
            });
        }
        String index = MessageKeys.index(prefix);
        Set<String> previous = redis.opsForSet().members(index);
        if (previous != null) {
            Set<String> removed = new HashSet<>(previous);
            removed.removeAll(hashes.keySet());
            if (!removed.isEmpty()) {
                redis.delete(removed);
                redis.opsForSet().remove(index, removed.toArray());
            }
        }
        if (!hashes.isEmpty()) {
            redis.opsForSet().add(index, hashes.keySet().toArray(String[]::new));
        }
        return hashes.size();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /** One hash per screen and code: type, default, default-language and lang:xx per PUBLISHED translation. */
    public Map<String, Map<String, String>> toHashes(Collection<MessageRow> rows, String defaultLanguage) {
        Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
        for (MessageRow row : rows) {
            Screen screen = new Screen(row.application(), row.module(), row.page(), row.component());
            if (!screen.isComplete() || row.code() == null || row.code().isBlank()) {
                continue;
            }
            Map<String, String> hash = hashes.computeIfAbsent(MessageKeys.key(prefix, screen, row.code().trim()), k -> {
                Map<String, String> fields = new HashMap<>();
                putIfPresent(fields, MessageKeys.FIELD_TYPE, row.contentType());
                putIfPresent(fields, MessageKeys.FIELD_DEFAULT, row.defaultValue());
                putIfPresent(fields, MessageKeys.FIELD_DEFAULT_LANGUAGE,
                        defaultLanguage == null ? null : defaultLanguage.trim().toLowerCase(Locale.ROOT));
                return fields;
            });
            if (row.language() != null && row.text() != null && !row.text().isBlank()) {
                hash.put(MessageKeys.languageField(row.language().trim().toLowerCase(Locale.ROOT)), row.text());
            }
        }
        return hashes;
    }

    private static void putIfPresent(Map<String, String> fields, String field, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(field, value);
        }
    }
}
