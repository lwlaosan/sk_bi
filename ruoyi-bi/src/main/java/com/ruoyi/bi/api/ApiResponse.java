package com.ruoyi.bi.api;

public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>("OK", "success", data, traceId);
    }
}

