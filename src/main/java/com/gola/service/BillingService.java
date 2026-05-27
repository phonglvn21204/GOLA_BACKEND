package com.gola.service;

import com.gola.dto.billing.*;
import com.gola.entity.Order;
import com.gola.entity.Price;
import com.gola.entity.Product;
import com.gola.entity.Subscription;
import com.gola.entity.enums.SubStatus;
import com.gola.exception.GolaException;
import com.gola.repository.OrderRepository;
import com.gola.repository.PriceRepository;
import com.gola.repository.ProductRepository;
import com.gola.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final String FREE_STRIPE_PRODUCT_ID = "prod_gola_free";

    private final ProductRepository productRepo;
    private final PriceRepository priceRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final OrderRepository orderRepo;

    @Transactional(readOnly = true)
    public List<PricingPlanResponse> listPricingPlans() {
        Map<UUID, List<Price>> pricesByProduct = priceRepo.findByIsActiveTrueOrderByAmountAsc().stream()
                .collect(Collectors.groupingBy(Price::getProductId));
        return productRepo.findByIsActiveTrueOrderByNameAsc().stream()
                .map(p -> mapPlan(p, pricesByProduct.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PremiumStatusResponse getPremiumStatus(UUID userId) {
        return subscriptionRepo.findFirstByUserIdAndStatusOrderByCurrentPeriodEndDesc(userId, SubStatus.ACTIVE)
                .map(this::mapPremium)
                .orElseGet(() -> PremiumStatusResponse.builder().premium(false).build());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listSubscriptions(UUID userId) {
        return subscriptionRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapSubscription).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(UUID userId) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapOrder).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID userId, UUID orderId) {
        Order order = orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> GolaException.notFound("Order"));
        return mapOrder(order);
    }

    public boolean hasActivePremium(UUID userId) {
        return getPremiumStatus(userId).isPremium();
    }

    private PremiumStatusResponse mapPremium(Subscription sub) {
        String planName = null;
        boolean premium = false;
        if (sub.getProductId() != null) {
            var product = productRepo.findById(sub.getProductId()).orElse(null);
            if (product != null) {
                planName = product.getName();
                premium = !FREE_STRIPE_PRODUCT_ID.equals(product.getStripeProductId());
            }
        }
        return PremiumStatusResponse.builder()
                .premium(premium)
                .subscriptionStatus(sub.getStatus())
                .activeSubscriptionId(sub.getId())
                .planName(planName)
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .build();
    }

    private PricingPlanResponse mapPlan(Product p, List<Price> prices) {
        return PricingPlanResponse.builder()
                .id(p.getId()).stripeProductId(p.getStripeProductId())
                .name(p.getName()).description(p.getDescription())
                .prices(prices.stream().map(this::mapPrice).toList())
                .build();
    }

    private PriceResponse mapPrice(Price price) {
        return PriceResponse.builder()
                .id(price.getId()).stripePriceId(price.getStripePriceId())
                .amount(price.getAmount()).currency(price.getCurrency())
                .intervalType(price.getIntervalType()).intervalCount(price.getIntervalCount())
                .build();
    }

    private SubscriptionResponse mapSubscription(Subscription s) {
        String productName = s.getProductId() == null ? null
                : productRepo.findById(s.getProductId()).map(Product::getName).orElse(null);
        return SubscriptionResponse.builder()
                .id(s.getId()).productId(s.getProductId()).priceId(s.getPriceId())
                .productName(productName).status(s.getStatus())
                .currentPeriodStart(s.getCurrentPeriodStart())
                .currentPeriodEnd(s.getCurrentPeriodEnd())
                .cancelAtPeriodEnd(s.isCancelAtPeriodEnd())
                .cancelledAt(s.getCancelledAt()).createdAt(s.getCreatedAt())
                .build();
    }

    private OrderResponse mapOrder(Order o) {
        return OrderResponse.builder()
                .id(o.getId()).priceId(o.getPriceId()).amount(o.getAmount())
                .currency(o.getCurrency()).status(o.getStatus())
                .stripeSessionId(o.getStripeSessionId())
                .createdAt(o.getCreatedAt()).updatedAt(o.getUpdatedAt())
                .build();
    }
}