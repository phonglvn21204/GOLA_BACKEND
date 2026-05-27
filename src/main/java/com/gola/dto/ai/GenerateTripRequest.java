package com.gola.dto.ai;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;
@Data public class GenerateTripRequest {
    @NotBlank private String origin;
    @NotBlank private String destination;
    @Min(1) @Max(30) private int days = 3;
    private List<String> interests;
    private String budget = "mid";
    private String language = "en";
}