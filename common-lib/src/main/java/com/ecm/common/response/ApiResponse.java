package com.ecm.common.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final Instant timestamp;

    private ApiResponse(boolean success, String code, String message, T data) {
        this(success, code, message, data, Instant.now());
    }

    // Every controller response is wrapped in this envelope, so a Feign client decoding
    // another service's response needs Jackson to be able to reconstruct it — without this
    // creator, deserialization fails with "no Creators, like default constructor, exist".
    @JsonCreator
    private ApiResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("data") T data,
            @JsonProperty("timestamp") Instant timestamp) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "SUCCESS", "Success", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, "SUCCESS", message, data);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
