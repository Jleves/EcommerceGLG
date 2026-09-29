package com.ashenox.starter.user;

import com.ashenox.starter.user.dto.UpdateUserEmailRequest;
import com.ashenox.starter.user.model.*;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.service.*;
import com.ashenox.starter.user.service.impl.AdminUserServiceImpl;
import com.ashenox.starter.auth.challenge.service.ChallengeService;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserEmailServiceTest {
    @Test void noOpWithMfaNeverInvalidatesAndRealChangeIsRejected() {
        var repository=mock(UserRepository.class);var guard=mock(AdminMutationGuard.class);
        var challenges=mock(ChallengeService.class);var resets=mock(PasswordResetTokenRepository.class);
        var sessions=mock(AuthSessionService.class);
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var service=new AdminUserServiceImpl(repository,mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                    mock(org.springframework.context.ApplicationEventPublisher.class),guard,challenges,resets,sessions,factory.getValidator());
            var actor=new AdminActor(1L,Role.SUPER_ADMIN,"sid");
            var target=User.builder().id(2L).email("old@example.com").role(Role.USER).emailMfaEnabled(true).build();
            when(guard.lock(actor,2L)).thenReturn(new AdminMutationGuard.LockedUsers(target,target,1));
            assertThat(service.updateEmail(2L,new UpdateUserEmailRequest(" OLD@EXAMPLE.COM "),actor).email()).isEqualTo("old@example.com");
            assertThatThrownBy(()->service.updateEmail(2L,new UpdateUserEmailRequest("new@example.com"),actor))
                    .isInstanceOf(AdminUserConflictException.class).extracting("code")
                    .isEqualTo(com.ashenox.starter.shared.error.ApiErrorCode.ADMIN_EMAIL_MFA_ENABLED);
            verifyNoInteractions(repository,challenges,resets,sessions);
        }
    }

    @Test void requestNormalizesBeforeValidationAndHasOnlyEmail() {
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            var request=new UpdateUserEmailRequest("  USER@Example.com  ");
            assertThat(request.email()).isEqualTo("user@example.com");
            assertThat(validator.validate(request)).isEmpty();
            for(String invalid:new String[]{null," ","bad","a".repeat(310)+"@example.com"})
                assertThat(validator.validate(new UpdateUserEmailRequest(invalid))).isNotEmpty();
            assertThat(UpdateUserEmailRequest.class.getRecordComponents()).extracting(java.lang.reflect.RecordComponent::getName)
                    .containsExactly("email");
        }
    }

    @Test void onlyKnownEmailConstraintIsTranslatedAndOtherDatabaseErrorsPropagate() {
        var repository=mock(UserRepository.class);var guard=mock(AdminMutationGuard.class);
        var challenges=mock(ChallengeService.class);var resets=mock(PasswordResetTokenRepository.class);
        var sessions=mock(AuthSessionService.class);
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var service=new AdminUserServiceImpl(repository,mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                    mock(org.springframework.context.ApplicationEventPublisher.class),guard,challenges,resets,sessions,factory.getValidator());
            var actor=new AdminActor(1L,Role.SUPER_ADMIN,"sid");
            for(String constraint:new String[]{"uk_users_email","users.uk_users_email","other_constraint"}) {
                var user=User.builder().id(2L).email("old@example.com").role(Role.USER).enabled(true).build();
                when(guard.lock(actor,2L)).thenReturn(new AdminMutationGuard.LockedUsers(user,user,1));
                var failure=new org.springframework.dao.DataIntegrityViolationException("constraint",
                        new org.hibernate.exception.ConstraintViolationException("failure",new java.sql.SQLException(),constraint));
                when(repository.saveAndFlush(user)).thenThrow(failure);
                if(constraint.equals("other_constraint"))
                    assertThatThrownBy(()->service.updateEmail(2L,new UpdateUserEmailRequest("new@example.com"),actor)).isSameAs(failure);
                else assertThatThrownBy(()->service.updateEmail(2L,new UpdateUserEmailRequest("new@example.com"),actor))
                        .isInstanceOf(AdminUserConflictException.class).extracting("code")
                        .isEqualTo(com.ashenox.starter.shared.error.ApiErrorCode.ADMIN_EMAIL_IN_USE);
            }
            verifyNoInteractions(challenges,resets,sessions);
        }
    }
}
