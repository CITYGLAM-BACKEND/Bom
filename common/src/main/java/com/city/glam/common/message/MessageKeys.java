package com.city.glam.common.message;

/**
 * Layout of the response messages in Redis database 2 ({@code RedisStores.messages()}). Master-Data-Service writes it;
 * every service reads it.
 *
 * <pre>
 * cityglam:messages:ORBIT/MASTER_DATA/CONTINENT/CONTINENT_FORM:20003     (hash, one per code and screen)
 *     type             = WARNING                                    content type: SUCCESS / ERROR / VALIDATION / WARNING
 *     default          = A continent with code {0} already exists   default_value (English)
 *     default-language = en                                         orbit_master_languages.is_default
 *     lang:en          = A continent with code {0} already exists   PUBLISHED translations
 *     lang:pt          = Já existe um continente com o código {0}
 * cityglam:messages:index                                                (set of every hash key above)
 * </pre>
 * Look a code up with {@code redis-cli -n 2 HGETALL "cityglam:messages:ORBIT/COMMON/COMMON/COMMON:30003"}.
 */
public final class MessageKeys {

    public static final String DEFAULT_PREFIX = "cityglam:messages:";

    public static final String FIELD_TYPE = "type";
    public static final String FIELD_DEFAULT = "default";
    public static final String FIELD_DEFAULT_LANGUAGE = "default-language";
    public static final String LANGUAGE_FIELD_PREFIX = "lang:";

    private MessageKeys() {
    }

    /** {@code cityglam:messages:ORBIT/COMMON/COMMON/COMMON:30003}. */
    public static String key(String prefix, Screen screen, String code) {
        return prefix + screen.path() + ":" + code;
    }

    /** Set holding every message key, so keys of deleted rows can be removed. */
    public static String index(String prefix) {
        return prefix + "index";
    }

    public static String languageField(String language) {
        return LANGUAGE_FIELD_PREFIX + language;
    }
}
