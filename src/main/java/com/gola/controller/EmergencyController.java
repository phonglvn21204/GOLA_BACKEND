package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/emergency")
@Tag(name = "Emergency")
public class EmergencyController {

    @GetMapping("/hotlines")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> hotlines() {
        return ResponseEntity.ok(ApiResponse.ok(List.of(
                Map.of("name", "Police", "phone", "113", "country", "VN"),
                Map.of("name", "Fire", "phone", "114", "country", "VN"),
                Map.of("name", "Ambulance", "phone", "115", "country", "VN"),
                Map.of("name", "Tourist Support", "phone", "1900 6888", "country", "VN")
        )));
    }
}
