package com.gola;

import com.gola.dto.safety.ReportRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Reports API integration")
class ReportControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void createReport() throws Exception {
        var req = new ReportRequest();
        req.setTargetType("POST");
        req.setTargetId(TestDataConstants.SEED_POST_ID);
        req.setReason("Spam content (integration test)");

        mockMvc.perform(post("/reports")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
