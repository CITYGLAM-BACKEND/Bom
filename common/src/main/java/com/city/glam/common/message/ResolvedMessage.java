package com.city.glam.common.message;

import com.city.glam.common.dto.ResponseStatus;

/**
 * Text of a code for one request, and the status of the code's content type.
 *
 * @param text   the message; the code itself when the database has no text for it
 * @param status from the content type of the key; {@code null} when the code has no row
 * @param source where the text came from, for logs and tests (e.g. {@code ORBIT/COMMON/COMMON/COMMON pt})
 */
public record ResolvedMessage(String text, ResponseStatus status, String source) {
}
