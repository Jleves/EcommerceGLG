package com.ashenox.starter.user.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import com.ashenox.starter.user.model.User;

public record UserPageResponse(List<UserResponse> content, int page, int size,
                               long totalElements, int totalPages) {
    public static UserPageResponse from(Page<User> result) {
        return new UserPageResponse(result.getContent().stream().map(UserResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
