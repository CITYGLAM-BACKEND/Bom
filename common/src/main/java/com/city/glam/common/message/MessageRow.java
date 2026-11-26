package com.city.glam.common.message;

/**
 * One row of the message query: an ACTIVE content key with one of its PUBLISHED translations. {@code language} and
 * {@code text} are {@code null} for a key without any published translation (then {@code defaultValue} is used).
 */
public record MessageRow(String application, String module, String page, String component, String code,
                         String contentType, String defaultValue, String language, String text) {
}
