package com.ashenox.starter.auth;

import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessSessionDatabaseFailureIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthSessionService service;
    @Autowired JWTUtil jwt;
    @MockitoSpyBean AuthSessionRepository sessions;

    @Test
    void failedSessionQueryDoesNotAuthorizeRequestOrCachePreviousSuccess() throws Exception {
        User user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash").role(Role.USER).enabled(true).build());
        var session = service.createSession(user);
        String sid = session.session().getId();
        Cookie access = new Cookie("ACCESS_TOKEN", jwt.generateToken(user.getId(), sid));
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isOk());
        doThrow(new DataAccessResourceFailureException("simulated database outage"))
                .when(sessions).findWithUserById(sid);
        // Filter infrastructure failures propagate to the servlet container, never to the controller.
        assertThatThrownBy(() -> mvc.perform(get("/api/auth/me").cookie(access)))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
