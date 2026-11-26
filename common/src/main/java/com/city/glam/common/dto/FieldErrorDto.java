package com.city.glam.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** One invalid field of the request (status 3). */
@Schema(name = "FieldError", description = "Validation error of one field")
public record FieldErrorDto(
        @Schema(description = "Field name as sent by the client", example = "username")
        String field,

        @Schema(description = "Specific validation code of this field and rule", example = "10101")
        String code,

        @Schema(description = "Description of the code in the client's language", example = "Enter your username or email address")
        String message) {
}
