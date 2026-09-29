package com.ashenox.starter.security.error;

public class InvalidAuthSessionException extends RuntimeException {
    public InvalidAuthSessionException() { super("La sesión es inválida o expiró."); }
}
