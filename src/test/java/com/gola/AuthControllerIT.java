package com.gola;

import com.gola.dto.auth.ForgotPasswordRequest;
import com.gola.dto.auth.LoginRequest;
import com.gola.dto.auth.RefreshRequest;
import com.gola.dto.auth.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth API integration")
class AuthControllerIT extends BaseIntegrationTest {

    @Test
    void register_returnsCreatedWithoutTokens() throws Exception {
        var req = new RegisterRequest();
        req.setEmail("it-user-" + UUID.randomUUID() + "@example.com");
        req.setPassword(TestDataConstants.SEED_PASSWORD);
        req.setDisplayName("IT User");
        req.setLocale("vi");
        req.setReferralCode("");

        var result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Account created. Please verify your email."))
                .andReturn();
        assertApiSuccess(result);
    }

    @Test
    void login_returnsTokens() throws Exception {
        var loginReq = new LoginRequest();
        loginReq.setEmail(TestDataConstants.SEED_USER_EMAIL);
        loginReq.setPassword(TestDataConstants.SEED_PASSWORD);
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void refresh_returnsNewTokens() throws Exception {
        loginAsSeedUser();
        var refresh = new RefreshRequest();
        refresh.setRefreshToken(refreshToken);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void logout_revokesTokens() throws Exception {
        loginAsSeedUser();
        mockMvc.perform(post("/auth/logout").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void forgotPassword_returnsOk() throws Exception {
        var req = new ForgotPasswordRequest();
        req.setEmail(TestDataConstants.SEED_USER_EMAIL);
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void protectedRoute_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isForbidden());
    }
}
