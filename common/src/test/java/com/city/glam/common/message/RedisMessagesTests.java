package com.city.glam.common.message;

import com.city.glam.common.dto.ResponseStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Publisher hash layout + reader lookup, with a map standing in for Redis. */
class RedisMessagesTests {

    private static final String P = MessageKeys.DEFAULT_PREFIX;
    private static final Screen FORM = new Screen("ORBIT", "MASTER_DATA", "CONTINENT", "CONTINENT_FORM");
    private static final Locale PT = Locale.forLanguageTag("pt");

    private static MessageRow row(Screen s, String code, String type, String def, String lang, String text) {
        return new MessageRow(s.application(), s.module(), s.page(), s.component(), code, type, def, lang, text);
    }

    private static final List<MessageRow> ROWS = List.of(
            row(Screen.COMMON, "30003", "ERROR", "Authentication required", "en", "Authentication required"),
            row(Screen.COMMON, "30003", "ERROR", "Authentication required", "pt", "Autenticação necessária"),
            row(Screen.COMMON, "00002", "SUCCESS", "Record created", "en", "Record created"),
            row(FORM, "00002", "SUCCESS", "Continent created", "pt", "Continente criado"),
            row(FORM, "20003", "WARNING", "A continent with code {0} already exists", "en", "A continent with code {0} already exists"),
            row(FORM, "20003", "WARNING", "A continent with code {0} already exists", "pt", "Já existe um continente com o código {0}"),
            row(FORM, "20002", "WARNING", "Language {0} is not available", null, null),
            row(FORM, "10203", "VALIDATION", null, null, null));

    /** What Redis would hold after a publish. */
    private static final Map<String, Map<String, String>> REDIS = new HashMap<>(new RedisMessagePublisher(null, P).toHashes(ROWS, "en"));

    private static final MessageReader READER = new RedisMessageReader(key -> REDIS.getOrDefault(key, Map.of()), P);

    @Test
    void publisherWritesOneHashPerScreenAndCode() {
        assertThat(REDIS).containsKey("cityglam:messages:ORBIT/MASTER_DATA/CONTINENT/CONTINENT_FORM:20003");
        assertThat(REDIS.get("cityglam:messages:ORBIT/MASTER_DATA/CONTINENT/CONTINENT_FORM:20003")).containsExactlyInAnyOrderEntriesOf(Map.of(
                "type", "WARNING",
                "default", "A continent with code {0} already exists",
                "default-language", "en",
                "lang:en", "A continent with code {0} already exists",
                "lang:pt", "Já existe um continente com o código {0}"));
        assertThat(REDIS).hasSize(6);
    }

    @Test
    void screenTextInTheClientsLanguageWithArguments() {
        ResolvedMessage m = READER.resolve("20003", FORM, PT, "AFRICA");
        assertThat(m.text()).isEqualTo("Já existe um continente com o código AFRICA");
        assertThat(m.status()).isEqualTo(ResponseStatus.CENTER_VALIDATION_ERROR);
    }

    @Test
    void regionalLanguageThenLanguageThenDefaultLanguage() {
        assertThat(READER.resolve("20003", FORM, Locale.forLanguageTag("pt-BR"), "EU").text()).isEqualTo("Já existe um continente com o código EU");
        assertThat(READER.resolve("20003", FORM, Locale.FRENCH, "EU").text()).isEqualTo("A continent with code EU already exists");
        assertThat(READER.resolve("20003", FORM, null, "EU").text()).isEqualTo("A continent with code EU already exists");
    }

    @Test
    void codeMissingOnTheScreenComesFromCommonAndNoScreenMeansCommon() {
        assertThat(READER.resolve("30003", FORM, PT).text()).isEqualTo("Autenticação necessária");
        assertThat(READER.resolve("00002", FORM, PT).text()).isEqualTo("Continente criado");
        assertThat(READER.resolve("00002", null, PT).text()).isEqualTo("Record created");
    }

    @Test
    void defaultValueThenTheCodeItself() {
        assertThat(READER.resolve("20002", FORM, PT, "xx").text()).isEqualTo("Language xx is not available");
        ResolvedMessage noText = READER.resolve("10203", FORM, PT);
        assertThat(noText.text()).isEqualTo("10203");
        assertThat(noText.status()).isEqualTo(ResponseStatus.FIELD_VALIDATION_ERROR);
        ResolvedMessage unknown = READER.resolve("99999", FORM, PT);
        assertThat(unknown.text()).isEqualTo("99999");
        assertThat(unknown.status()).isNull();
    }

    @Test
    void redisDownMeansCodesOnly() {
        MessageReader down = new RedisMessageReader(key -> {
            throw new IllegalStateException("Connection refused");
        }, P);
        ResolvedMessage m = down.resolve("30003", FORM, PT);
        assertThat(m.text()).isEqualTo("30003");
        assertThat(m.status()).isNull();
    }

    @Test
    void screenHeadersMustAllBePresentAndAreCaseInsensitive() {
        assertThat(Screen.fromHeaders(h -> switch (h) {
            case Screen.HEADER_APPLICATION -> "orbit";
            case Screen.HEADER_MODULE -> "master_data";
            case Screen.HEADER_PAGE -> "continent";
            case Screen.HEADER_COMPONENT -> " continent_form ";
            default -> null;
        })).isEqualTo(FORM);
        assertThat(Screen.fromHeaders(h -> h.equals(Screen.HEADER_COMPONENT) ? null : "X")).isNull();
    }

    @Test
    void textsWithApostrophesAndBracesStayIntact() {
        assertThat(MessageTexts.fill("You don't have {0} {braces}", "x")).isEqualTo("You don't have x {braces}");
    }

    @Test
    void statusIsDerivedFromHttpWhenRedisHasNone() {
        assertThat(ResponseStatus.derive(201, false)).isEqualTo(ResponseStatus.SUCCESS);
        assertThat(ResponseStatus.derive(400, true)).isEqualTo(ResponseStatus.FIELD_VALIDATION_ERROR);
        assertThat(ResponseStatus.derive(409, false)).isEqualTo(ResponseStatus.CENTER_VALIDATION_ERROR);
        assertThat(ResponseStatus.derive(401, false)).isEqualTo(ResponseStatus.ERROR);
    }

    @Test
    void readsRedisOncePerCacheTtlAndUsesTheLastCopyWhenRedisIsDown() {
        AtomicInteger lookups = new AtomicInteger();
        AtomicBoolean redisDown = new AtomicBoolean();
        AtomicLong now = new AtomicLong(1_000);
        MessageReader cached = new RedisMessageReader(key -> {
            lookups.incrementAndGet();
            if (redisDown.get()) {
                throw new IllegalStateException("connection refused");
            }
            return REDIS.getOrDefault(key, Map.of());
        }, P, Duration.ofSeconds(60), now::get);

        String first = cached.resolve("20003", FORM, PT, "AFRICA").text();
        int afterFirst = lookups.get();
        assertThat(cached.resolve("20003", FORM, PT, "AFRICA").text()).isEqualTo(first);
        assertThat(lookups.get()).as("served from memory").isEqualTo(afterFirst);

        now.addAndGet(61_000);
        redisDown.set(true);
        assertThat(cached.resolve("20003", FORM, PT, "AFRICA").text()).as("older copy while Redis is down").isEqualTo(first);
        assertThat(lookups.get()).isGreaterThan(afterFirst);
    }

    @Test
    void madeUpScreensAreNeverRemembered() {
        AtomicInteger lookups = new AtomicInteger();
        MessageReader cached = new RedisMessageReader(key -> {
            lookups.incrementAndGet();
            return Map.of();
        }, P, Duration.ofSeconds(60), System::currentTimeMillis);
        Screen invented = new Screen("X", "Y", "Z", "W");
        cached.resolve("20003", invented, PT);
        cached.resolve("20003", invented, PT);
        // Nothing found = nothing kept: every call asks Redis again (no memory growth from client headers)
        assertThat(lookups.get()).isEqualTo(4);
    }
}
