package com.ashenox.starter.user;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.service.ChallengeException;
import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.security.error.InvalidRefreshTokenException;
import com.ashenox.starter.security.error.InvalidTokenException;
import com.ashenox.starter.user.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserReactivationTest extends AdminUserEmailTestSupport {
    @Test void cyclePreservesMfaAndOldCredentialsStayInvalid() throws Exception {
        target.setEmailMfaEnabled(true);
        target = users.saveAndFlush(target);
        var previousHash = target.getPasswordHash();
        var pending = challenges.start(target.getId(), ChallengePurpose.LOGIN, null);
        var reset = reset();
        var oldAccess = access(target, origin);
        service.deactivate(target.getId(), actor);
        mvc.perform(post("/api/admin/users/{id}/reactivate", target.getId()).cookie(adminAccess).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.mfaEnabled").value(true));
        assertThat(current().getPasswordHash()).isEqualTo(previousHash);
        assertThat(current().getSecurityVersion()).isEqualTo(1);
        assertThat(current().getEmail()).isEqualTo(target.getEmail());
        assertThat(resets.findById(reset.getId()).orElseThrow().getUsedAt()).isNotNull();
        assertThat(challengeRepository.findById(pending.challenge().getId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThat(sessionRepository.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
        mvc.perform(get("/api/auth/me").cookie(oldAccess)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> sessions.rotate(origin.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> resetService.validateToken("reset-" + target.getId())).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> challenges.verifyAndConsume(pending.cookieValue(), ChallengePurpose.LOGIN,
                target.getId(), null, pending.code())).isInstanceOf(ChallengeException.class);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + target.getEmail() + "\",\"password\":\"secure-password\"}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andExpect(cookie().exists(AuthCookieService.MFA_CHALLENGE));
    }

    @Test void cycleWithoutMfaAllowsFreshLoginAndNoOpDoesNotChangeVersion() throws Exception {
        service.deactivate(target.getId(), actor);
        service.reactivate(target.getId(), actor);
        var before = current().getUpdatedAt();
        mvc.perform(post("/api/admin/users/{id}/reactivate", target.getId()).cookie(adminAccess).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        assertThat(current().getUpdatedAt()).isEqualTo(before);
        assertThat(current().getSecurityVersion()).isEqualTo(1);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + target.getEmail() + "\",\"password\":\"secure-password\"}"))
                .andExpect(status().isOk()).andExpect(cookie().exists(AuthCookieService.ACCESS_TOKEN));
    }

    @Test void missingTargetAndUnauthorizedCallers() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/reactivate", Long.MAX_VALUE).cookie(adminAccess).with(csrf()))
                .andExpect(status().isNotFound());
        String path = "/api/admin/users/" + target.getId() + "/reactivate";
        mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(path).cookie(adminAccess)).andExpect(status().isForbidden());
        for (var role : new Role[]{Role.USER, Role.ADMIN}) {
            var caller = user(role);
            var session = sessions.createSession(caller);
            mvc.perform(post(path).cookie(access(caller, session)).with(csrf())).andExpect(status().isForbidden());
        }
    }

    @Test void oldTargetAccessCannotReactivateItself() throws Exception {
        var oldAccess = access(target, origin);
        service.deactivate(target.getId(), actor);
        mvc.perform(post("/api/admin/users/{id}/reactivate", target.getId()).cookie(oldAccess).with(csrf()))
                .andExpect(status().isUnauthorized());
        assertThat(current().isEnabled()).isFalse();
    }
}
