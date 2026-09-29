package com.ashenox.starter.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.ashenox.starter.user.support.EmailNormalizer;

public record UpdateUserEmailRequest(
        @NotBlank @Email @Size(max = 320) String email) {
    public UpdateUserEmailRequest {
        if (email != null && !email.isBlank()) email = EmailNormalizer.normalize(email);
    }
}
