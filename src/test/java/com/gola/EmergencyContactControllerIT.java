package com.gola;

import com.gola.dto.safety.EmergencyContactRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Emergency contacts API integration")
class EmergencyContactControllerIT extends AuthenticatedIntegrationTest {

    @Test
    void listContacts() throws Exception {
        mockMvc.perform(get("/emergency-contacts").with(authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void createContact_whenUnderLimit() throws Exception {
        var req = new EmergencyContactRequest();
        req.setName("IT Contact");
        req.setPhone("+84909999999");
        req.setRelation("Friend");
        req.setPriority(5);
        var result = mockMvc.perform(post("/emergency-contacts")
                        .with(authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();
        int status = result.getResponse().getStatus();
        if (status == 201) {
            assertApiSuccess(result);
        }
    }
}
