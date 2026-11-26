package com.city.glam.common.message;

import java.util.Locale;

/** Text and status of a response code for one request. */
@FunctionalInterface
public interface MessageReader {

    /** Used when there is no Redis: every message is the code itself. */
    MessageReader CODES_ONLY = (code, screen, locale, args) -> new ResolvedMessage(code, null, "no message store");

    /**
     * @param screen the request's screen; {@code null} = COMMON only
     * @param locale the client's language; {@code null} = default language
     */
    ResolvedMessage resolve(String code, Screen screen, Locale locale, Object... args);
}
