package com.gola.service;

import com.gola.dto.trip.ExpenseRequest;
import com.gola.dto.trip.ExpenseResponse;
import com.gola.entity.Expense;
import com.gola.exception.GolaException;
import com.gola.repository.ExpenseRepository;
import com.gola.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepo;
    private final TripMemberRepository memberRepo;

    @Transactional
    public ExpenseResponse addExpense(UUID tripId, UUID payerId, ExpenseRequest req) {
        if (!memberRepo.existsByTripIdAndUserId(tripId, payerId)) {
            throw GolaException.forbidden();
        }

        Expense expense = Expense.builder()
                .tripId(tripId)
                .payerId(payerId)
                .amount(req.getAmount())
                .currency(req.getCurrency())
                .description(req.getDescription())
                .build();

        return toResponse(expenseRepo.save(expense));
    }

    public List<ExpenseResponse> getExpenses(UUID tripId, UUID userId) {
        if (!memberRepo.existsByTripIdAndUserId(tripId, userId)) {
            throw GolaException.forbidden();
        }
        return expenseRepo.findByTripIdOrderByCreatedAtDesc(tripId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteExpense(UUID expenseId, UUID userId) {
        Expense expense = expenseRepo.findById(expenseId)
                .orElseThrow(() -> GolaException.notFound("Expense"));

        if (!expense.getPayerId().equals(userId)) {
            throw GolaException.forbidden();
        }

        expenseRepo.delete(expense);
    }

    private ExpenseResponse toResponse(Expense e) {
        return ExpenseResponse.builder()
                .id(e.getId())
                .tripId(e.getTripId())
                .payerId(e.getPayerId())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
