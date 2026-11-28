package com.city.glam.common.message.export;

/** What a language file gets for a message that has no PUBLISHED translation in that language. Nothing is translated. */
public enum MissingTranslationPolicy {

    /** The text of the fallback language, else the content key's default value (English). */
    FALLBACK,

    /** The content key's default value. */
    DEFAULT_VALUE,

    /** Leave the message out of that language's file (the frontend then uses its own fallback). */
    OMIT
}
