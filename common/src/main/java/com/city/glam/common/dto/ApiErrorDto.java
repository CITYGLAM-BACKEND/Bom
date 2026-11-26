package com.city.glam.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Error payload of a failed response. Never contains stack traces, SQL or other internal details. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(name = "ApiError", description = "Error payload; present for failed responses that carry extra detail")
public record ApiErrorDto(
        @Schema(description = "Invalid fields (status 3 only)")
        List<FieldErrorDto> fieldErrors) {

    public ApiErrorDto {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ApiErrorDto ofFields(List<FieldErrorDto> fieldErrors) {
        return new ApiErrorDto(fieldErrors);
    }
}
