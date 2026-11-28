package com.city.glam.common.message.export;

import com.city.glam.common.message.MessageRow;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Turns the message rows of the database into one catalog per language:
 * {@code module key -> code -> text}, for example {@code "orbit:auth:login:login_form" -> "10101" -> "Enter your ..."}.
 * <p>
 * The module key is application, module, page and component in lower case, joined with colons, like the Redis key
 * of the message without its prefix and code (ORBIT / MASTER_DATA / CONTINENT / CONTINENT_FORM =
 * {@code orbit:master_data:continent:continent_form}).
 * Texts are copied as they are (placeholders {0}, {1} ... and any Unicode stay untouched). Keys and codes are sorted,
 * so the same data always gives the same file. Nothing is translated: a missing text follows the
 * {@link MissingTranslationPolicy}.
 */
public final class MessageCatalog {

    private static final Pattern CODE = Pattern.compile("\\d{5}");

    private MessageCatalog() {
    }

    /**
     * @param languages        the languages to build (one catalog each, same order)
     * @param fallbackLanguage language for {@link MissingTranslationPolicy#FALLBACK}; may be null
     */
    public static Result build(Collection<MessageRow> rows, List<String> languages, String fallbackLanguage,
                               MissingTranslationPolicy policy) {
        // One entry per (module key, code): its default value and its texts per language
        Map<String, Map<String, Entry>> entries = new TreeMap<>();
        int invalid = 0;
        for (MessageRow row : rows) {
            String moduleKey = moduleKey(row);
            if (moduleKey == null || row.code() == null || !CODE.matcher(row.code()).matches()) {
                invalid++;
                continue;
            }
            Entry entry = entries.computeIfAbsent(moduleKey, k -> new TreeMap<>())
                    .computeIfAbsent(row.code(), c -> new Entry(row.defaultValue()));
            if (row.language() != null && row.text() != null) {
                entry.texts().put(row.language().toLowerCase(Locale.ROOT), row.text());
            }
        }
        String fallback = fallbackLanguage == null || fallbackLanguage.isBlank() ? null : fallbackLanguage.toLowerCase(Locale.ROOT);
        Map<String, Map<String, Map<String, String>>> byLanguage = new LinkedHashMap<>();
        Map<String, Integer> missing = new LinkedHashMap<>();
        int messages = entries.values().stream().mapToInt(Map::size).sum();
        for (String language : languages) {
            String lang = language.toLowerCase(Locale.ROOT);
            Map<String, Map<String, String>> catalog = new TreeMap<>();
            int notTranslated = 0;
            for (Map.Entry<String, Map<String, Entry>> module : entries.entrySet()) {
                for (Map.Entry<String, Entry> code : module.getValue().entrySet()) {
                    Entry e = code.getValue();
                    String text = e.texts().get(lang);
                    if (text == null) {
                        notTranslated++;
                        text = switch (policy) {
                            case FALLBACK -> fallback != null && e.texts().get(fallback) != null ? e.texts().get(fallback) : e.defaultValue();
                            case DEFAULT_VALUE -> e.defaultValue();
                            case OMIT -> null;
                        };
                    }
                    if (text != null) {
                        catalog.computeIfAbsent(module.getKey(), k -> new TreeMap<>()).put(code.getKey(), text);
                    }
                }
            }
            byLanguage.put(lang, catalog);
            missing.put(lang, notTranslated);
        }
        return new Result(byLanguage, messages, invalid, missing);
    }

    /** {@code orbit:auth:login:login_form} for ORBIT / AUTH / LOGIN / LOGIN_FORM; null when a part is missing. */
    public static String moduleKey(MessageRow row) {
        List<String> parts = Stream.of(row.application(), row.module(), row.page(), row.component()).toList();
        if (parts.stream().anyMatch(p -> p == null || p.isBlank())) {
            return null;
        }
        return parts.stream().map(p -> p.strip().toLowerCase(Locale.ROOT)).collect(Collectors.joining(":"));
    }

    private record Entry(String defaultValue, Map<String, String> texts) {
        Entry(String defaultValue) {
            this(defaultValue, new HashMap<>());
        }
    }

    /**
     * @param catalogs           language -> module key -> code -> text
     * @param messages           distinct (module key, code) pairs in the source
     * @param invalidRows        rows left out (no complete screen or not a 5-digit code)
     * @param missingTranslations language -> messages without a translation in that language
     */
    public record Result(Map<String, Map<String, Map<String, String>>> catalogs, int messages, int invalidRows,
                         Map<String, Integer> missingTranslations) {
    }
}
