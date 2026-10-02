package com.ashenox.starter.catalog.characteristic;

import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.service.CharacteristicService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CharacteristicDeleteTest {
    private static final String PATH = "/api/catalog/characteristics/";

    @Autowired MockMvc mvc;
    @Autowired CharacteristicService characteristics;

    @Test
    void missingDefinitionReturnsApiError() throws Exception {
        mvc.perform(delete(PATH + "999999999").with(user("editor").roles("USER")).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void everyCatalogRoleCanDeleteAndResponseHasNoBody() throws Exception {
        for (String role : new String[]{"SUPER_ADMIN", "ADMIN", "USER"}) {
            long id = characteristics.create(new CreateCharacteristicRequest("Borrar " + UUID.randomUUID())).id();
            mvc.perform(delete(PATH + id).with(user("editor").roles(role)).with(csrf()))
                    .andExpect(status().isNoContent())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEmpty());
            mvc.perform(delete(PATH + id).with(user("editor").roles(role)).with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void csrfAuthenticationAndRoleAreRequired() throws Exception {
        long id = characteristics.create(new CreateCharacteristicRequest("Protegida " + UUID.randomUUID())).id();
        mvc.perform(delete(PATH + id).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete(PATH + id).with(user("editor").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH + id).with(user("other").roles("OTHER")).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(characteristics.getById(id).id()).isEqualTo(id);
    }
}
