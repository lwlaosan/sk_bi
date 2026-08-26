package com.ruoyi.bi.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.ruoyi.bi")
public class BiExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiError("BI_REQUEST_FORBIDDEN", "无操作权限", Map.of(), TraceIdFilter.current(request)));
    }

    @ExceptionHandler(BiException.class)
    ResponseEntity<ApiError> handleBi(BiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(new ApiError(ex.code(), ex.getMessage(), ex.details(), TraceIdFilter.current(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        FieldError field = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = field == null ? "请求参数不合法" : field.getField() + ": " + field.getDefaultMessage();
        return ResponseEntity.badRequest().body(new ApiError("BI_REQUEST_INVALID", message, Map.of(), TraceIdFilter.current(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError("BI_QUERY_FAILED", "服务处理失败，请使用 traceId 联系管理员", Map.of(), TraceIdFilter.current(request)));
    }

    record ApiError(String code, String message, Map<String, Object> details, String traceId) {}
}
