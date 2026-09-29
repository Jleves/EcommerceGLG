package com.ashenox.starter.user.controller;

import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.user.dto.CreateUserRequest;
import com.ashenox.starter.user.dto.UserResponse;
import com.ashenox.starter.user.service.AdminUserService;
import com.ashenox.starter.user.service.AdminActor;
import com.ashenox.starter.user.dto.UserPageResponse;
import com.ashenox.starter.log.filter.RequestLoggingFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping("/{id}/reactivate")
    public UserResponse reactivate(@PathVariable Long id,
                                   @AuthenticationPrincipal AuthenticatedUser principal,
                                   HttpServletRequest servletRequest) {
        return adminUserService.reactivate(id, new AdminActor(principal.id(), principal.role(),
                (String) servletRequest.getAttribute(RequestLoggingFilter.SESSION_ID_ATTRIBUTE)));
    }

    @PostMapping("/{id}/deactivate")
    public UserResponse deactivate(@PathVariable Long id,
                                   @AuthenticationPrincipal AuthenticatedUser principal,
                                   HttpServletRequest servletRequest) {
        return adminUserService.deactivate(id, new AdminActor(principal.id(), principal.role(),
                (String) servletRequest.getAttribute(RequestLoggingFilter.SESSION_ID_ATTRIBUTE)));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/email")
    public UserResponse updateEmail(@PathVariable Long id,
                                    @Valid @RequestBody com.ashenox.starter.user.dto.UpdateUserEmailRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser principal,
                                    HttpServletRequest servletRequest) {
        return adminUserService.updateEmail(id, request, new AdminActor(principal.id(), principal.role(),
                (String) servletRequest.getAttribute(RequestLoggingFilter.SESSION_ID_ATTRIBUTE)));
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal,
            HttpServletRequest servletRequest) {
        UserResponse response = adminUserService.create(request, new AdminActor(principal.id(), principal.role(), (String) servletRequest.getAttribute(RequestLoggingFilter.SESSION_ID_ATTRIBUTE)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public UserPageResponse list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @AuthenticationPrincipal AuthenticatedUser principal,
                                 HttpServletRequest servletRequest) {
        return adminUserService.list(page, size, new AdminActor(principal.id(), principal.role(), (String) servletRequest.getAttribute(RequestLoggingFilter.SESSION_ID_ATTRIBUTE)));
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id,
                                @AuthenticationPrincipal AuthenticatedUser principal,
                                HttpServletRequest servletRequest) {
        return adminUserService.getById(id, new AdminActor(principal.id(), principal.role(), (String) servletRequest.getAttribute(RequestLoggingFilter.SESSION_ID_ATTRIBUTE)));
    }
}
