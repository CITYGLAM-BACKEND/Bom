package com.city.glam.common.message;

import com.city.glam.common.dto.ResponseStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Picks the text of one code from its Redis hash ({@link MessageKeys}): the client's language (e.g. {@code pt-BR},
 * then {@code pt}), else the default language, else {@code default}, else the code itself; then fills {0}, {1} ...
 */
public final class MessageTexts {

    private MessageTexts() {
    }

    public static ResolvedMessage resolve(String code, Map<String, String> hash, Locale locale, String source,
                                          Object... args) {
        ResponseStatus status = ResponseStatus.fromContentType(hash.get(MessageKeys.FIELD_TYPE));
        for (String language : languages(locale, hash.get(MessageKeys.FIELD_DEFAULT_LANGUAGE))) {
            String text = hash.get(MessageKeys.languageField(language));
            if (text != null && !text.isBlank()) {
                return new ResolvedMessage(fill(text, args), status, source + " " + language);
            }
        }
        String fallback = hash.get(MessageKeys.FIELD_DEFAULT);
        if (fallback != null && !fallback.isBlank()) {
            return new ResolvedMessage(fill(fallback, args), status, source + " default");
        }
        return new ResolvedMessage(code, status, source + " no text");
    }

    /** Replaces {0}, {1} ... without MessageFormat, so texts may contain apostrophes and braces freely. */
    public static String fill(String text, Object... args) {
        if (args == null || args.length == 0) {
            return text;
        }
        String result = text;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }

    private static List<String> languages(Locale locale, String defaultLanguage) {
        LinkedHashSet<String> order = new LinkedHashSet<>();
        if (locale != null && !locale.getLanguage().isEmpty()) {
            order.add(locale.toLanguageTag().toLowerCase(Locale.ROOT));
            order.add(locale.getLanguage().toLowerCase(Locale.ROOT));
        }
        if (defaultLanguage != null && !defaultLanguage.isBlank()) {
            order.add(defaultLanguage.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(order);
    }
}
