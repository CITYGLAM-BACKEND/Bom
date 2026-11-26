package com.city.glam.common.web;

import com.city.glam.common.dto.ApiErrorDto;
import com.city.glam.common.dto.ApiResponseDto;
import com.city.glam.common.dto.FieldErrorDto;
import com.city.glam.common.dto.ResponseStatus;
import com.city.glam.common.message.MessageReader;
import com.city.glam.common.message.ResolvedMessage;
import com.city.glam.common.message.Screen;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Locale;

/**
 * Builds every {@link ApiResponseDto} of a servlet service. Java passes only the code and the HTTP status; the
 * message (in the client's language, for the client's screen) and the status 1-4 come from Redis through the
 * {@link MessageReader}; Master-Data-Service keeps Redis filled from the database.
 *
 * <p>Screen = headers X-Application / X-Module / X-Page / X-Component (all four, else COMMON).
 * Language = Accept-Language (else the default language). Methods without a request argument use the current request.
 */
public class ApiResponses {

    private final MessageReader messages;

    public ApiResponses(MessageReader messages) {
        this.messages = messages;
    }

    // ---------------------------------------------------------------- success (HTTP 2xx)

    public <T> ApiResponseDto<T> success(String code, HttpStatus http, T data) {
        return success(currentRequest(), code, http, data);
    }

    public <T> ApiResponseDto<T> success(HttpServletRequest request, String code, HttpStatus http, T data) {
        if (!http.is2xxSuccessful()) {
            throw new IllegalArgumentException("A successful response needs a 2xx status, got " + http);
        }
        return build(request, code, http, data, null, false);
    }

    // ---------------------------------------------------------------- failures

    /** A failed check, e.g. {@code failure("20003", HttpStatus.CONFLICT, code)}; args fill {0}, {1} ... */
    public <T> ApiResponseDto<T> failure(String code, HttpStatus http, Object... args) {
        return failure(currentRequest(), code, http, args);
    }

    public <T> ApiResponseDto<T> failure(HttpServletRequest request, String code, HttpStatus http, Object... args) {
        if (http.is2xxSuccessful()) {
            throw new IllegalArgumentException("A failed response needs a non-2xx status, got " + http);
        }
        return build(request, code, http, null, null, false, args);
    }

    /** One invalid field with the text of its code. */
    public FieldErrorDto field(HttpServletRequest request, String field, String code, Object... args) {
        return new FieldErrorDto(field, code, message(request, code, args).text());
    }

    /** Status 3, HTTP 400; code and message are those of the first invalid field. */
    public <T> ApiResponseDto<T> fieldErrors(HttpServletRequest request, List<FieldErrorDto> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            throw new IllegalArgumentException("A field validation response needs at least one field error");
        }
        FieldErrorDto first = fieldErrors.getFirst();
        return ApiResponseDto.of(ResponseStatus.FIELD_VALIDATION_ERROR, HttpStatus.BAD_REQUEST.value(), first.code(),
                first.message(), null, ApiErrorDto.ofFields(fieldErrors));
    }

    // ---------------------------------------------------------------- helpers

    /** HTTP status of the response = the body's httpStatus. */
    public static <T> ResponseEntity<ApiResponseDto<T>> toEntity(ApiResponseDto<T> body) {
        return ResponseEntity.status(body.httpStatus()).body(body);
    }

    public ResolvedMessage message(HttpServletRequest request, String code, Object... args) {
        return messages.resolve(code, screen(request), locale(request), args);
    }

    public static Screen screen(HttpServletRequest request) {
        return request == null ? null : Screen.fromHeaders(request::getHeader);
    }

    /** The Accept-Language language; {@code null} (= default language) when the client sends none. */
    public static Locale locale(HttpServletRequest request) {
        return request == null || request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) == null ? null : request.getLocale();
    }

    private <T> ApiResponseDto<T> build(HttpServletRequest request, String code, HttpStatus http, T data,
                                        ApiErrorDto error, boolean fieldValidation, Object... args) {
        ResolvedMessage resolved = message(request, code, args);
        ResponseStatus status = resolved.status() != null ? resolved.status()
                : ResponseStatus.derive(http.value(), fieldValidation);
        return ApiResponseDto.of(status, http.value(), code, resolved.text(), data, error);
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }
}
