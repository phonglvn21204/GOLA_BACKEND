package com.gola;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gola.dto.auth.LoginRequest;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIf("com.gola.BaseIntegrationTest#isDockerAvailable")
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>(
                DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("goladb")
                .withUsername("gola")
                .withPassword("gola123");
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
    }

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    protected String accessToken;
    protected String refreshToken;
    protected UUID currentUserId;

    protected void clearAuth() {
        accessToken = null;
        refreshToken = null;
        currentUserId = null;
    }

    protected void login(String email, String password) throws Exception {
        var req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        parseAuthResponse(result);
    }

    protected void loginAsSeedUser() throws Exception {
        login(TestDataConstants.SEED_USER_EMAIL, TestDataConstants.SEED_PASSWORD);
    }

    protected void loginAsSeedUser2() throws Exception {
        login(TestDataConstants.SEED_USER2_EMAIL, TestDataConstants.SEED_PASSWORD);
    }

    protected void loginAsAdmin() throws Exception {
        login(TestDataConstants.SEED_ADMIN_EMAIL, TestDataConstants.SEED_PASSWORD);
    }

    protected void parseAuthResponse(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken = root.path("data").path("accessToken").asText();
        refreshToken = root.path("data").path("refreshToken").asText();
        currentUserId = UUID.fromString(root.path("data").path("user").path("id").asText());
    }

    protected String getToken() {
        if (accessToken == null) {
            throw new IllegalStateException("Not logged in - call login() first");
        }
        return accessToken;
    }

    protected RequestPostProcessor authHeader() {
        return request -> {
            request.addHeader("Authorization", "Bearer " + getToken());
            return request;
        };
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected void assertApiSuccess(JsonNode root) {
        assertTrue(root.path("success").asBoolean(), "Expected success=true: " + root);
    }

    protected void assertApiSuccess(MvcResult result) throws Exception {
        assertApiSuccess(readJson(result));
    }

    protected ResultMatcher jsonSuccess() {
        return result -> assertApiSuccess(readJson(result));
    }
}