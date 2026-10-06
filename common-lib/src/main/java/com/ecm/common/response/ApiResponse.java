package com.ecm.common.response;

import com.ecm.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Top-level response envelope shared by every service. {@code message} is always a static,
 * machine-readable key (never request-specific text); details belong in {@code errors}.
 */
public class ApiResponse<T> {

    public static final String SUCCESS_MESSAGE = "SUCCESS";

    private final T data;
    private final String message;
    private final List<ErrorDetail> errors;

    // Every controller response is wrapped in this envelope, so a Feign client decoding
    // another service's response needs Jackson to be able to reconstruct it — without this
    // creator, deserialization fails with "no Creators, like default constructor, exist".
    @JsonCreator
    private ApiResponse(
            @JsonProperty("data") T data,
            @JsonProperty("message") String message,
            @JsonProperty("errors") List<ErrorDetail> errors) {
        this.data = data;
        this.message = message;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, SUCCESS_MESSAGE, List.of());
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, List<ErrorDetail> errors) {
        return new ApiResponse<>(null, errorCode.name(), errors);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String detail) {
        return error(errorCode, List.of(new ErrorDetail(null, detail)));
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public List<ErrorDetail> getErrors() {
        return errors;
    }

    public record ErrorDetail(String field, String message) {
    }
}
