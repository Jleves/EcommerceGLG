package com.ashenox.starter.auth.challenge.model;

public enum AuthRateLimitScope {
    MFA_CODE_ISSUE,
    MFA_CODE_FAILURE,
    LOGIN_ACCOUNT,
    LOGIN_IP
}
