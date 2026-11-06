package com.ashenox.starter.subscription;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSubscriptionTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminSubscriptionService service;

    @BeforeEach
    void clean() { jdbc.update("DELETE FROM subscriptions"); }

    @Test
    void listsBothChannelsIndependentlyWithPagesAndWithdrawals() throws Exception {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        LocalDateTime stored = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        jdbc.update("INSERT INTO subscriptions (email, whatsapp, consentimiento_email_en, consentimiento_whatsapp_en, baja_email_en) VALUES (?, ?, ?, ?, ?)",
                "both@example.com", "+5492984123456", stored, stored, stored);
        for (int i = 0; i < 21; i++) jdbc.update(
                "INSERT INTO subscriptions (email, consentimiento_email_en) VALUES (?, ?)",
                "user" + i + "@example.com", stored);

        mvc.perform(get("/api/admin/subscriptions").with(user("admin").roles("ADMIN"))
                        .param("channel", "EMAIL").param("page", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(22))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.content[0].contact").value("user20@example.com"));
        mvc.perform(get("/api/admin/subscriptions").with(user("admin").roles("SUPER_ADMIN"))
                        .param("channel", "EMAIL").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[1].contact").value("both@example.com"))
                .andExpect(jsonPath("$.content[1].active").value(false));
        mvc.perform(get("/api/admin/subscriptions").with(user("admin").roles("ADMIN"))
                        .param("channel", "WHATSAPP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].active").value(true));
    }

    @Test
    void validatesParametersAndPermissions() throws Exception {
        for (String query : new String[]{"channel=BAD", "channel=EMAIL&page=-1", "channel=EMAIL&size=101"}) {
            mvc.perform(get("/api/admin/subscriptions?" + query).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/subscriptions").param("channel", "EMAIL"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/subscriptions").with(user("buyer").roles("USER"))
                        .param("channel", "EMAIL"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/subscriptions/export").with(user("buyer").roles("USER"))
                        .param("channel", "EMAIL"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/subscriptions/export").param("channel", "EMAIL"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exportsAllActiveContactsButNotWithdrawnContacts() throws Exception {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        LocalDateTime stored = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        for (int i = 0; i < 25; i++) jdbc.update(
                "INSERT INTO subscriptions (email, consentimiento_email_en) VALUES (?, ?)",
                "user" + i + "@example.com", stored);
        jdbc.update("INSERT INTO subscriptions (email, consentimiento_email_en, baja_email_en) VALUES (?, ?, ?)",
                "withdrawn@example.com", stored, stored);
        try (var bytes = new ByteArrayOutputStream()) {
            service.exportActive(SubscriptionRequest.Channel.EMAIL, bytes);
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
                var sheet = workbook.getSheetAt(0);
                assertThat(sheet.getLastRowNum()).isEqualTo(25);
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Contacto");
                assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("user24@example.com");
                assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("2026-01-01 09:00:00");
            }
        }
    }

    @Test
    void exportsValidEmptyWorkbook() throws Exception {
        try (var bytes = new ByteArrayOutputStream()) {
            service.exportActive(SubscriptionRequest.Channel.WHATSAPP, bytes);
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
                assertThat(workbook.getSheetAt(0).getLastRowNum()).isZero();
            }
        }
    }

    @Test
    void authorizedExportReturnsCompleteXlsxAcrossBatches() throws Exception {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        LocalDateTime stored = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        for (int i = 0; i < 501; i++) jdbc.update(
                "INSERT INTO subscriptions (whatsapp, consentimiento_whatsapp_en) VALUES (?, ?)",
                "+5492984" + String.format("%06d", i), stored);
        var pending = mvc.perform(get("/api/admin/subscriptions/export")
                        .with(user("admin").roles("ADMIN")).param("channel", "WHATSAPP"))
                .andExpect(request().asyncStarted()).andReturn();
        var response = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getContentType()).contains("spreadsheetml.sheet");
        assertThat(response.getHeader("Content-Disposition")).contains("suscriptores-whatsapp-");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(501);
        }
    }
}
