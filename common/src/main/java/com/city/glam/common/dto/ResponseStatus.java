package com.city.glam.common.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * City Glam's own outcome of a request, sent as a number in {@code ApiResponseDto.status}.
 * The client decides how to show the message from this value, not from the HTTP status.
 *
 * <p>It comes from the content type of the code's row in the database (SUCCESS, ERROR, VALIDATION, WARNING). When
 * the code has no row (or the database cannot be read) it is derived from the HTTP status, see {@link #derive}.
 */
@Schema(name = "ResponseStatus", type = "integer", allowableValues = {"1", "2", "3", "4"},
        description = "1 = success, 2 = error, 3 = field-level validation error, 4 = center-level validation message")
public enum ResponseStatus {

    /** The request worked; {@code data} holds the payload. Content type SUCCESS. */
    SUCCESS(1),

    /** Technical or security error (500, 503, 401, 403 ...). Content type ERROR. */
    ERROR(2),

    /** One or more fields are invalid; {@code error.fieldErrors} says which ones. Content type VALIDATION. */
    FIELD_VALIDATION_ERROR(3),

    /** Business rule message shown in the center of the screen (record exists ...). Content type WARNING. */
    CENTER_VALIDATION_ERROR(4);

    private final int value;

    ResponseStatus(int value) {
        this.value = value;
    }

    @JsonValue
    public int value() {
        return value;
    }

    @JsonCreator
    public static ResponseStatus of(int value) {
        for (ResponseStatus status : values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown response status: " + value);
    }

    /** Status of a {@code orbit_master_content_type.code}; {@code null} for any other content type. */
    public static ResponseStatus fromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType.trim().toUpperCase()) {
            case "SUCCESS" -> SUCCESS;
            case "ERROR" -> ERROR;
            case "VALIDATION" -> FIELD_VALIDATION_ERROR;
            case "WARNING" -> CENTER_VALIDATION_ERROR;
            default -> null;
        };
    }

    /**
     * Status when the database gives none: 2xx = 1, field validation = 3, 401 / 403 / 5xx = 2, any other 4xx = 4.
     */
    public static ResponseStatus derive(int httpStatus, boolean fieldValidation) {
        if (httpStatus >= 200 && httpStatus < 300) {
            return SUCCESS;
        }
        if (fieldValidation) {
            return FIELD_VALIDATION_ERROR;
        }
        if (httpStatus == 401 || httpStatus == 403 || httpStatus >= 500) {
            return ERROR;
        }
        return httpStatus >= 400 ? CENTER_VALIDATION_ERROR : ERROR;
    }
}
