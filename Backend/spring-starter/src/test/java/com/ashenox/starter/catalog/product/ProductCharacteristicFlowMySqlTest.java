package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProductCharacteristicFlowMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CategoryRepository categories;

    @Test
    void createsDefinitionAndTwoProductsWithDistinctValues() throws Exception {
        String suffix = UUID.randomUUID().toString();
        Category category = categories.saveAndFlush(Category.builder().nombre("Categoría " + suffix)
                .nombreNormalizado(suffix).icono("icon").build());
        String response = mvc.perform(post("/api/catalog/characteristics").with(user("admin").roles("ADMIN"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Marca " + suffix + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asLong();
        for (String brand : new String[]{"Acme", "Otra"}) {
            String body = "{\"nombre\":\"Arena\",\"categoriaId\":" + category.getId()
                    + ",\"precioReferencia\":10.00,\"disponible\":true,\"caracteristicas\":[{\"caracteristicaId\":"
                    + id + ",\"valor\":\"" + brand + "\"}]}";
            mvc.perform(post("/api/catalog/products").with(user("editor").roles("USER"))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.caracteristicas[0].caracteristicaId").value(id))
                    .andExpect(jsonPath("$.caracteristicas[0].nombre").value("Marca " + suffix))
                    .andExpect(jsonPath("$.caracteristicas[0].valor").value(brand));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_characteristics WHERE characteristic_id = ?", Integer.class, id))
                .isEqualTo(2);
    }
}
