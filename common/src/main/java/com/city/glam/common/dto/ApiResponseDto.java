package com.city.glam.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;

/**
 * Common envelope of every City Glam API response, successful or not.
 *
 * <pre>
 * { "status": 1, "httpStatus": 201, "code": "00002", "message": "Continent created", "data": {...}, "timestamp": "..." }
 * { "status": 4, "httpStatus": 409, "code": "20003", "message": "A continent with code AFRICA already exists", ... }
 * { "status": 3, "httpStatus": 400, "code": "10203", "message": "...",
 *   "error": { "fieldErrors": [ { "field": "code", "code": "10203", "message": "..." } ] } }
 * </pre>
 *
 * The message is never hard-coded: services build this record through {@code ApiResponses}, which reads the text of
 * the code from the message cache (loaded from the database).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Common envelope for successful and failed API responses")
public record ApiResponseDto<T>(
        @Schema(description = "1 = success, 2 = error, 3 = field-level validation error, 4 = center-level validation message",
                example = "1", type = "integer", allowableValues = {"1", "2", "3", "4"})
        ResponseStatus status,

        @Schema(description = "HTTP status code", example = "200")
        int httpStatus,

        @Schema(description = "Unique 5-digit response code (orbit_master_content_key.code)", example = "00001")
        String code,

        @Schema(description = "Text of the code in the client's language, from the database", example = "Request completed")
        String message,

        @Schema(description = "Response payload; present for successful responses")
        T data,

        @Schema(description = "Error payload; present for field validation errors")
        ApiErrorDto error,

        @Schema(description = "Time when the response was created (UTC)")
        Instant timestamp) {

    public ApiResponseDto {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public static <T> ApiResponseDto<T> of(ResponseStatus status, int httpStatus, String code, String message,
                                           T data, ApiErrorDto error) {
        return new ApiResponseDto<>(status, httpStatus, code, message, data, error, Instant.now());
    }
}
