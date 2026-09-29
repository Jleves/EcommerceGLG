package com.ashenox.starter.auth.challenge;

import com.ashenox.starter.auth.challenge.model.ChallengeDeliveryState;
import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.service.ChallengeCrypto;
import com.ashenox.starter.auth.challenge.service.ChallengeFactory;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChallengeFactoryTest {
    private final AppProperties properties = new AppProperties();
    private final ChallengeCrypto crypto;
    private final ChallengeFactory factory;

    ChallengeFactoryTest() {
        properties.getSecurity().getMfa().setHmacSecret("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        crypto = new ChallengeCrypto(properties);
        factory = new ChallengeFactory(crypto, properties);
    }

    @Test
    void createsPendingChallengeWithOnlyProtectedSecretsInEntity() {
        User user = new User();
        user.setId(42L);
        var result = factory.create(user, ChallengePurpose.LOGIN, 7, null);
        var challenge = result.challenge();

        assertThat(challenge.getPurpose()).isEqualTo(ChallengePurpose.LOGIN);
        assertThat(challenge.getSecurityVersion()).isEqualTo(7);
        assertThat(challenge.getOriginSessionId()).isNull();
        assertThat(challenge.getCodeGeneration()).isEqualTo(1);
        assertThat(challenge.getDeliveryState()).isEqualTo(ChallengeDeliveryState.PENDING);
        assertThat(challenge.getFailedAttempts()).isZero();
        assertThat(challenge.getResendCount()).isZero();
        assertThat(challenge.getExpiresAt()).isAfter(challenge.getCreatedAt());
        assertThat(result.code()).matches("[0-9]{6}");
        assertThat(result.challengeSecret()).isNotEqualTo(challenge.getSecretHash());
        assertThat(result.code()).isNotEqualTo(challenge.getCodeHmac());
        assertThat(crypto.matchesSecret(result.challengeSecret(), challenge.getSecretHash())).isTrue();
        assertThat(crypto.matchesCode(challenge.getId(), challenge.getPurpose(), 1,
                result.code(), challenge.getCodeHmac())).isTrue();
        assertThat(result.toString()).doesNotContain(result.code(), result.challengeSecret());
    }

    @Test
    void hmacBindsCodeToIdPurposeAndGenerationIncludingLeadingZero() {
        String hmac = crypto.codeHmac("id-a", ChallengePurpose.LOGIN, 1, "000123");
        assertThat(crypto.matchesCode("id-a", ChallengePurpose.LOGIN, 1, "000123", hmac)).isTrue();
        assertThat(crypto.matchesCode("id-b", ChallengePurpose.LOGIN, 1, "000123", hmac)).isFalse();
        assertThat(crypto.matchesCode("id-a", ChallengePurpose.ENABLE, 1, "000123", hmac)).isFalse();
        assertThat(crypto.matchesCode("id-a", ChallengePurpose.LOGIN, 2, "000123", hmac)).isFalse();
    }

    @Test
    void requiresOriginOnlyForSettingsPurposes() {
        User user = new User();
        user.setId(42L);
        assertThatThrownBy(() -> factory.create(user, ChallengePurpose.ENABLE, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(user, ChallengePurpose.LOGIN, 0, "session"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(factory.create(user, ChallengePurpose.DISABLE, 0, "session").challenge().getOriginSessionId())
                .isEqualTo("session");
    }
}
