package com.city.glam.common.message.export;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Export of the response messages as one JSON file per language for the frontend ({@code msg_en.json} ...).
 *
 * @param enabled            off by default: only an environment with a place to write the files switches it on
 * @param outputDirectory    folder the files are written to (local: the frontend's {@code public/i18n}; Kubernetes: a
 *                           mounted volume). Never a developer machine path in a shared environment
 * @param cron               when the export runs (Spring cron, seconds first); default every day at midnight
 * @param timezone           time zone of the cron
 * @param languages          language codes to export; empty = every ACTIVE language of orbit_master_languages
 * @param fallbackLanguage   language used by {@link MissingTranslationPolicy#FALLBACK}; empty = the default language
 * @param missingTranslation what a file gets for a message without a translation in its language
 * @param filePrefix         file name = prefix + language + ".json"
 */
@ConfigurationProperties(prefix = "cityglam.messages.export")
public record MessageExportProperties(
        @DefaultValue("false") boolean enabled,
        String outputDirectory,
        @DefaultValue("0 0 0 * * *") String cron,
        @DefaultValue("Asia/Kolkata") String timezone,
        @DefaultValue List<String> languages,
        String fallbackLanguage,
        @DefaultValue("FALLBACK") MissingTranslationPolicy missingTranslation,
        @DefaultValue("msg_") String filePrefix) {

    public MessageExportProperties {
        languages = languages == null ? List.of() : List.copyOf(languages);
    }

    /** {@code msg_en.json} for {@code en}. */
    public String fileName(String language) {
        return filePrefix + language + ".json";
    }
}
