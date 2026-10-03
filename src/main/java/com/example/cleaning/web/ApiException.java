package com.example.cleaning.web;

/** 业务异常 -> 4xx + JSON 错误体 */
public class ApiException extends RuntimeException {
    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String msg) { return new ApiException(400, msg); }
    public static ApiException forbidden(String msg) { return new ApiException(403, msg); }
    public static ApiException notFound(String msg) { return new ApiException(404, msg); }
    public static ApiException conflict(String msg) { return new ApiException(409, msg); }
}
