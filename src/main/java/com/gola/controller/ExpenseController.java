package com.gola.controller;

import com.gola.dto.common.ApiResponse;
import com.gola.dto.trip.ExpenseRequest;
import com.gola.dto.trip.ExpenseResponse;
import com.gola.security.SecurityUtils;
import com.gola.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/trips/{tripId}/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Trip expenses tracking")
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    @Operation(summary = "Add an expense to a trip")
    public ResponseEntity<ApiResponse<ExpenseResponse>> addExpense(
            @PathVariable UUID tripId,
            @Valid @RequestBody ExpenseRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Expense added",
                expenseService.addExpense(tripId, SecurityUtils.getCurrentUserId(), req)));
    }

    @GetMapping
    @Operation(summary = "Get expenses for a trip")
    public ResponseEntity<ApiResponse<List<ExpenseResponse>>> getExpenses(
            @PathVariable UUID tripId) {
        return ResponseEntity.ok(ApiResponse.ok(
                expenseService.getExpenses(tripId, SecurityUtils.getCurrentUserId())));
    }

    @DeleteMapping("/{expenseId}")
    @Operation(summary = "Delete an expense")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(
            @PathVariable UUID tripId,
            @PathVariable UUID expenseId) {
        // Validation could be added to ensure expense belongs to trip
        expenseService.deleteExpense(expenseId, SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok("Expense deleted", null));
    }
}
