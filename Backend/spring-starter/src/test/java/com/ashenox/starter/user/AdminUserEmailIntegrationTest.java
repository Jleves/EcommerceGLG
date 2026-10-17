package com.ashenox.starter.user;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.test.context.TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:adminuseremailintegrationtest;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
class AdminUserEmailIntegrationTest extends AdminUserEmailTestSupport {}
