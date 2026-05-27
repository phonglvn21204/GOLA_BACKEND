package com.gola.dto.safety;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data public class EmergencyContactRequest {
    @NotBlank private String name;
    @NotBlank private String phone;
    private String relation;
    private int priority = 1;
}