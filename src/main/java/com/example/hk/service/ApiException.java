package com.example.hk.service;

/** 业务异常，由 ExceptionMapper 统一转成 JSON 错误。 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
