package com.ashenox.starter.user.service;

import com.ashenox.starter.user.dto.CreateUserRequest;
import com.ashenox.starter.user.dto.UserResponse;
import com.ashenox.starter.user.dto.UserPageResponse;

public interface AdminUserService {

    UserResponse create(CreateUserRequest request, AdminActor actor);

    UserPageResponse list(int page, int size, AdminActor actor);

    UserResponse getById(Long id, AdminActor actor);

    UserResponse updateEmail(Long id, com.ashenox.starter.user.dto.UpdateUserEmailRequest request, AdminActor actor);

    UserResponse deactivate(Long id, AdminActor actor);

    UserResponse reactivate(Long id, AdminActor actor);
}
