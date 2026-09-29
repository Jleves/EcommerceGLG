package com.ashenox.starter.user;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.security.error.InvalidTokenException;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.service.AdminUserConflictException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserDeactivationTest extends AdminUserEmailTestSupport {
    @Test void deactivationKeepsMfaAndRowsButInvalidatesCredentials() throws Exception {
        target.setEmailMfaEnabled(true); target=users.saveAndFlush(target);
        var pending=challenges.start(target.getId(), ChallengePurpose.LOGIN, null);
        var reset=reset();
        mvc.perform(post("/api/admin/users/{id}/deactivate", target.getId()).cookie(adminAccess).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.mfaEnabled").value(true));
        assertThat(current().getSecurityVersion()).isEqualTo(1);
        assertThat(challengeRepository.findById(pending.challenge().getId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThat(resets.findById(reset.getId()).orElseThrow().getUsedAt()).isNotNull();
        assertThat(sessionRepository.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
        mvc.perform(get("/api/auth/me").cookie(access(target,origin))).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->resetService.validateToken("reset-"+target.getId())).isInstanceOf(InvalidTokenException.class);
        var before=current().getUpdatedAt();
        mvc.perform(post("/api/admin/users/{id}/deactivate",target.getId()).cookie(adminAccess).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(current().getSecurityVersion()).isEqualTo(1);
        assertThat(current().getUpdatedAt()).isEqualTo(before);
    }

    @Test void selfAndLastSuperAdminAreConflicts() throws Exception {
        mvc.perform(post("/api/admin/users/{id}/deactivate",admin.getId()).cookie(adminAccess).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ADMIN_SELF_DEACTIVATION"));
        var other=user(Role.SUPER_ADMIN);
        mvc.perform(post("/api/admin/users/{id}/deactivate",other.getId()).cookie(adminAccess).with(csrf()))
                .andExpect(status().isOk());
        assertThat(users.findById(other.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test void inactiveAccountCannotIssueValidateOrConsumeReset() throws Exception {
        var token=reset();
        target.setEnabled(false); target=users.saveAndFlush(target);
        assertThatThrownBy(()->resetService.validateToken("reset-"+target.getId())).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(()->resetService.resetPassword("reset-"+target.getId(),"new-password-123"))
                .isInstanceOf(InvalidTokenException.class);
        resetService.createPasswordResetToken(target.getEmail());
        assertThat(resets.findById(token.getId()).orElseThrow().getTokenHash()).isEqualTo(token.getTokenHash());
        var inactiveResponse=mvc.perform(post("/api/auth/forgot-password").with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+target.getEmail()+"\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var unknownResponse=mvc.perform(post("/api/auth/forgot-password").with(csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"email\":\"absent@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(inactiveResponse).isEqualTo(unknownResponse);
    }

    @Test void deactivationRequiresSuperAdminAndCsrf() throws Exception {
        String path="/api/admin/users/"+target.getId()+"/deactivate";
        mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(path).cookie(adminAccess)).andExpect(status().isForbidden());
        for(var role:new Role[]{Role.ADMIN,Role.USER}) {
            var other=user(role); var session=sessions.createSession(other);
            mvc.perform(post(path).cookie(access(other,session)).with(csrf())).andExpect(status().isForbidden());
        }
        assertThat(current().isEnabled()).isTrue();
    }

    @Test void rollbackRestoresUserChallengesResetAndSession() {
        var pending=challenges.start(target.getId(),ChallengePurpose.ENABLE,origin.session().getId());
        var token=reset();
        doAnswer(invocation->{invocation.callRealMethod();throw new IllegalStateException("injected revocation failure");})
                .when(sessions).revokeAllForUser(target.getId());
        assertThatThrownBy(()->service.deactivate(target.getId(),actor)).isInstanceOf(IllegalStateException.class);
        assertThat(current().isEnabled()).isTrue();
        assertThat(current().getSecurityVersion()).isZero();
        assertThat(challengeRepository.findById(pending.challenge().getId()).orElseThrow().getInvalidatedAt()).isNull();
        assertThat(resets.findById(token.getId()).orElseThrow().getUsedAt()).isNull();
        assertThat(sessionRepository.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNull();
    }

}
