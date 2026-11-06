package com.ashenox.starter.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubscriptionControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM subscriptions");
        jdbc.update("DELETE FROM auth_rate_limits WHERE scope = 'SUBSCRIPTION_IP'");
    }

    @Test
    void savesIndependentChannelsAndDoesNotDuplicateActiveConsent() throws Exception {
        subscribe("EMAIL", "  USER@Example.com ").andExpect(status().isNoContent());
        Timestamp first = jdbc.queryForObject("SELECT consentimiento_email_en FROM subscriptions WHERE email = ?",
                Timestamp.class, "user@example.com");
        subscribe("EMAIL", "user@example.com").andExpect(status().isNoContent());
        subscribe("WHATSAPP", "2984 123456").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT consentimiento_email_en FROM subscriptions WHERE email = ?",
                Timestamp.class, "user@example.com")).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT consentimiento_whatsapp_en FROM subscriptions WHERE whatsapp = ?",
                Timestamp.class, "+5492984123456")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT consentimiento_whatsapp_en FROM subscriptions WHERE email = ?",
                Timestamp.class, "user@example.com")).isNull();
    }

    @Test
    void reactivationClearsWithdrawalAndRecordsNewConsent() throws Exception {
        subscribe("EMAIL", "user@example.com").andExpect(status().isNoContent());
        Timestamp old = Timestamp.from(Instant.parse("2020-01-01T00:00:00Z"));
        jdbc.update("UPDATE subscriptions SET consentimiento_email_en = ?, baja_email_en = ? WHERE email = ?",
                old, old, "user@example.com");
        subscribe("EMAIL", "user@example.com").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT consentimiento_email_en FROM subscriptions WHERE email = ?",
                Timestamp.class, "user@example.com")).isAfter(old);
        assertThat(jdbc.queryForObject("SELECT baja_email_en FROM subscriptions WHERE email = ?",
                Timestamp.class, "user@example.com")).isNull();
    }

    @Test
    void rejectsInvalidRequestsAndRequiresCsrf() throws Exception {
        mvc.perform(post("/api/public/subscriptions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"EMAIL\",\"contact\":\"a@b.com\",\"consent\":true}"))
                .andExpect(status().isForbidden());
        for (String body : new String[]{
                "{\"channel\":\"EMAIL\",\"contact\":\"bad\",\"consent\":true}",
                "{\"channel\":\"WHATSAPP\",\"contact\":\"123\",\"consent\":true}",
                "{\"channel\":\"EMAIL\",\"contact\":\"a@b.com\",\"consent\":false}",
                "{\"channel\":\"EMAIL\",\"contact\":\"a@b.com\"}"}) {
            mvc.perform(post("/api/public/subscriptions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions", Integer.class)).isZero();
        mvc.perform(get("/api/public/subscriptions")).andExpect(status().isUnauthorized());
    }

    @Test
    void limitsBothChannelsTogetherByIp() throws Exception {
        for (int i = 0; i < 10; i++) subscribe("EMAIL", "user" + i + "@example.com")
                .andExpect(status().isNoContent());
        subscribe("WHATSAPP", "2984 123456").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscriptions", Integer.class)).isEqualTo(10);
    }

    private org.springframework.test.web.servlet.ResultActions subscribe(String channel, String contact) throws Exception {
        return mvc.perform(post("/api/public/subscriptions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"channel\":\"" + channel + "\",\"contact\":\"" + contact +
                        "\",\"consent\":true}"));
    }
}
