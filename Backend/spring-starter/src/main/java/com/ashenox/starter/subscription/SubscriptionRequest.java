package com.ashenox.starter.subscription;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubscriptionRequest(
        @NotNull Channel channel,
        @NotBlank @Size(max = 254) String contact,
        @NotNull @AssertTrue Boolean consent
) {
    public enum Channel { EMAIL, WHATSAPP }
}
