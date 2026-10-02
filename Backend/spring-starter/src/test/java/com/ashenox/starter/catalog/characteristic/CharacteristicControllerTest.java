package com.ashenox.starter.catalog.characteristic;

import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CharacteristicControllerTest {
    private static final String PATH = "/api/catalog/characteristics";
    @Autowired MockMvc mvc;
    @Autowired CharacteristicRepository repository;

    @Test
    void emptyListAndAlphabeticalOrderAreStable() throws Exception {
        repository.deleteAll();
        mvc.perform(get(PATH).with(user("reader").roles("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
        String suffix = java.util.UUID.randomUUID().toString();
        repository.saveAndFlush(Characteristic.builder().nombre("Zeta " + suffix)
                .nombreNormalizado("zeta-" + suffix).build());
        repository.saveAndFlush(Characteristic.builder().nombre("Alfa " + suffix)
                .nombreNormalizado("alfa-" + suffix).build());
        mvc.perform(get(PATH).with(user("reader").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Alfa " + suffix))
                .andExpect(jsonPath("$[1].nombre").value("Zeta " + suffix));
    }

    @Test
    void createsListsAndGetsDefinitionsForEveryCatalogRole() throws Exception {
        for (String role : new String[]{"SUPER_ADMIN", "ADMIN", "USER"}) {
            String name = "Marca " + role;
            mvc.perform(post(PATH).with(user("editor").roles(role)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"  " + name + "  \"}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value(name))
                    .andExpect(jsonPath("$.id").isNumber());
            var definition = repository.findAll().stream().filter(c -> c.getNombre().equals(name)).findFirst().orElseThrow();
            mvc.perform(get(PATH + "/" + definition.getId()).with(user("reader").roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value(name));
            mvc.perform(get(PATH).with(user("reader").roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").isNumber());
        }
    }

    @Test
    void rejectsDuplicatesInvalidNamesAndMissingIds() throws Exception {
        String name = "Unica" + java.util.UUID.randomUUID();
        mvc.perform(post(PATH).with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"" + name + "\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post(PATH).with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"  " + name.toLowerCase() + "  \"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
        for (String body : new String[]{"{\"nombre\":\"   \"}", "{\"nombre\":\"" + "x".repeat(161) + "\"}"}) {
            mvc.perform(post(PATH).with(user("editor").roles("USER")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post(PATH).with(user("editor").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(get(PATH + "/999999999").with(user("reader").roles("USER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void catalogRequiresRoleAndCsrfAndAdminUsersStaysRestricted() throws Exception {
        String body = "{\"nombre\":\"Marca\"}";
        mvc.perform(post(PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(user("editor").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user("other").roles("OTHER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(user("other").roles("OTHER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        assertThat(repository.existsByNombreNormalizado("marca")).isFalse();
    }
}
