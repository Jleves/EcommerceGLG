package com.ashenox.starter.user.service;

import com.ashenox.starter.shared.error.ApiErrorCode;
import lombok.Getter;

@Getter
public class AdminUserConflictException extends RuntimeException {
    private final ApiErrorCode code;

    public AdminUserConflictException(ApiErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}
