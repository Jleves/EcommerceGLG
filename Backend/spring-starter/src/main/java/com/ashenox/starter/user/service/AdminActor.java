package com.ashenox.starter.user.service;

import com.ashenox.starter.user.model.Role;

/** Actor supplied by the authenticated request context, never by request JSON. */
public record AdminActor(Long id, Role role, String sessionId) {
}
