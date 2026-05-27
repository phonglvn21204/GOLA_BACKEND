package com.gola.controller;

import com.gola.exception.GolaException;
import com.gola.dto.common.ApiResponse;
import com.gola.dto.safety.EmergencyContactRequest;
import com.gola.entity.EmergencyContact;
import com.gola.repository.EmergencyContactRepository;
import com.gola.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/emergency-contacts")
@RequiredArgsConstructor
@Tag(name = "Emergency Contacts")
public class EmergencyContactController {
    private final EmergencyContactRepository repo;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmergencyContact>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(repo.findByUserIdOrderByPriorityAsc(SecurityUtils.getCurrentUserId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmergencyContact>> create(@Valid @RequestBody EmergencyContactRequest req) {
        UUID uid = SecurityUtils.getCurrentUserId();
        if (repo.countByUserId(uid) >= 5) throw GolaException.badRequest("Maximum 5 emergency contacts allowed");
        var c = EmergencyContact.builder().userId(uid).name(req.getName())
            .phone(req.getPhone()).relation(req.getRelation()).priority(req.getPriority()).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Contact added", repo.save(c)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        var c = repo.findById(id).orElseThrow(() -> GolaException.notFound("Contact"));
        if (!c.getUserId().equals(SecurityUtils.getCurrentUserId())) throw GolaException.forbidden();
        repo.delete(c);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}