package com.ashenox.starter.auth.model;

import com.ashenox.starter.user.dto.UserDTO;

public record LoginResponse(String status, UserDTO user) {
    public LoginResponse(UserDTO user) { this("AUTHENTICATED", user); }
}
