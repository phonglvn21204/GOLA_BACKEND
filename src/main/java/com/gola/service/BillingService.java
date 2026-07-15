package com.gola.service;

import com.gola.dto.billing.*;
import com.gola.entity.Order;
import com.gola.entity.Price;
import com.gola.entity.Product;
import com.gola.entity.Subscription;
import com.gola.entity.enums.SubStatus;
import com.gola.entity.enums.PaymentStatus;
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
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingService {

    private static final String FREE_PLAN = "FREE";
    private static final String BASIC_TRIP_PLAN = "BASIC_TRIP";
    private static final String GROUP_PRO_TRIP_PLAN = "GROUP_PRO_TRIP";
    private static final List<String> PLAN_ORDER = List.of(FREE_PLAN, BASIC_TRIP_PLAN, GROUP_PRO_TRIP_PLAN);
    private static final Map<String, List<String>> PLAN_FEATURES = Map.of(
            FREE_PLAN, List.of(
                    "Kế hoạch du lịch có sẵn",
                    "Theo dõi trực tiếp cơ bản",
                    "Thử thách nhiệm vụ cơ bản",
                    "Truy cập cộng đồng"
            ),
            BASIC_TRIP_PLAN, List.of(
                    "AI sửa lộ trình thời gian thực",
                    "Tạo kế hoạch tùy chỉnh",
                    "Tích hợp SOS",
                    "Album du lịch AI",
                    "Hỗ trợ ưu tiên"
            ),
            GROUP_PRO_TRIP_PLAN, List.of(
                    "Lên kế hoạch nhóm",
                    "Đồng bộ thời gian thực",
                    "Bản đồ nhiệm vụ nâng cao",
                    "Album & video reel nhóm AI",
                    "Sách du lịch tải về",
                    "Tất cả tính năng Cơ bản"
            )
    );

    private final ProductRepository productRepo;
    private final PriceRepository priceRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final OrderRepository orderRepo;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<PricingPlanResponse> listPricingPlans() {
        Map<UUID, List<Price>> pricesByProduct = priceRepo.findByIsActiveTrueOrderByAmountAsc().stream()
                .collect(Collectors.groupingBy(Price::getProductId));
        Map<String, PricingPlanResponse> plansBySlug = productRepo.findByIsActiveTrueOrderByNameAsc().stream()
                .filter(p -> PLAN_ORDER.contains(planSlug(p)))
                .map(p -> mapPlan(p, pricesByProduct.getOrDefault(p.getId(), List.of())))
                .collect(Collectors.toMap(PricingPlanResponse::getSlug, p -> p, (a, b) -> a));
        return PLAN_ORDER.stream()
                .map(plansBySlug::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public PremiumStatusResponse getPremiumStatus(UUID userId) {
        return subscriptionRepo.findFirstByUserIdAndStatusOrderByCurrentPeriodEndDesc(userId, SubStatus.ACTIVE)
                .map(this::mapPremium)
                .orElseGet(() -> PremiumStatusResponse.builder()
                        .premium(false)
                        .currentPlan(FREE_PLAN)
                        .planName("Miễn phí")
                        .features(PLAN_FEATURES.get(FREE_PLAN))
                        .daysRemaining(0)
                        .expired(false)
                        .build());
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

    @Transactional
    public Order createVietQrPendingOrder(UUID userId, String priceId, String orderCode, String transferContent, Instant expiresAt) {
        String normalizedPriceId = priceId == null ? "" : priceId.trim();
        Price price = resolveActivePrice(normalizedPriceId);
        subscriptionRepo.findFirstByUserIdAndStatusOrderByCurrentPeriodEndDesc(userId, SubStatus.ACTIVE)
                .filter(this::isCurrentlyActive)
                .filter(sub -> price.getProductId() != null && price.getProductId().equals(sub.getProductId()))
                .ifPresent(sub -> {
                    throw GolaException.badRequest("Gói này đang được sử dụng");
                });

        Order order = Order.builder()
                .userId(userId)
                .priceId(price.getId())
                .paymentProvider("VIETQR")
                .orderCode(orderCode)
                .transferContent(transferContent)
                .amount(price.getAmount())
                .currency(price.getCurrency() == null ? "vnd" : price.getCurrency().toLowerCase())
                .status(PaymentStatus.PENDING)
                .expiresAt(expiresAt)
                .build();
        return orderRepo.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOwnedOrderEntity(UUID userId, UUID orderId) {
        return orderRepo.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> GolaException.notFound("Order"));
    }

    @Transactional(readOnly = true)
    public Order getOrderEntity(UUID orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> GolaException.notFound("Order"));
    }

    @Transactional
    public Order completeManualOrder(UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> GolaException.notFound("Order"));
        return markOrderPaid(order, null, null, null, "MANUAL");
    }

    @Transactional
    public Order completeBankConfirmedOrder(Order order, Long paidAmount, String bankReferenceCode, String bankTransactionId, String provider) {
        return markOrderPaid(order, paidAmount, bankReferenceCode, bankTransactionId, provider);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findByTransferContentOrOrderCode(String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = orderCode.trim().toUpperCase();
        return orderRepo.findFirstByOrderCodeIgnoreCaseOrTransferContentIgnoreCase(normalized, normalized);
    }

    @Transactional(readOnly = true)
    public boolean vietQrOrderCodeExists(String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            return false;
        }
        String normalized = orderCode.trim().toUpperCase();
        return orderRepo.existsByOrderCodeIgnoreCaseOrTransferContentIgnoreCase(normalized, normalized);
    }

    private Order markOrderPaid(Order order, Long paidAmount, String bankReferenceCode, String bankTransactionId, String provider) {
        if (order.getStatus() != PaymentStatus.SUCCEEDED) {
            Instant now = Instant.now();
            order.setStatus(PaymentStatus.SUCCEEDED);
            order.setPaidAt(now);
            order.setPaidAmount(paidAmount);
            order.setBankReferenceCode(bankReferenceCode);
            order.setBankTransactionId(bankTransactionId);
            order.setAutoConfirmedAt(provider == null || "MANUAL".equalsIgnoreCase(provider) ? null : now);
            order.setAutoConfirmProvider(provider);
            order.setUpdatedAt(now);
            orderRepo.save(order);
            activateSubscription(order);
            notifyPaymentSuccess(order);
        }
        return order;
    }

    private void notifyPaymentSuccess(Order order) {
        try {
            notificationService.notifyPayment(
                    order.getUserId(),
                    "PAYMENT_SUCCESS",
                    "Thanh toán thành công",
                    "Thanh toán thành công. Quyền lợi Pro đã được cập nhật.",
                    order.getId(),
                    "/profile"
            );
        } catch (Exception ignored) {
            // Payment confirmation must not fail if notification storage is unavailable.
        }
    }

    private void activateSubscription(Order order) {
        if (order.getPriceId() == null) return;
        Price price = priceRepo.findById(order.getPriceId()).orElse(null);
        if (price == null) return;
        Instant now = Instant.now();
        subscriptionRepo.findByUserIdOrderByCreatedAtDesc(order.getUserId()).stream()
                .filter(sub -> sub.getStatus() == SubStatus.ACTIVE)
                .filter(this::isCurrentlyActive)
                .forEach(sub -> {
                    sub.setStatus(SubStatus.CANCELLED);
                    sub.setCancelledAt(now);
                    sub.setUpdatedAt(now);
                    subscriptionRepo.save(sub);
                });
        Instant periodEnd = calculatePeriodEnd(now, price);
        Subscription subscription = Subscription.builder()
                .userId(order.getUserId())
                .productId(price.getProductId())
                .priceId(price.getId())
                .status(SubStatus.ACTIVE)
                .currentPeriodStart(now)
                .currentPeriodEnd(periodEnd)
                .build();
        subscriptionRepo.save(subscription);
    }

    private Instant calculatePeriodEnd(Instant start, Price price) {
        int count = price.getIntervalCount() == null || price.getIntervalCount() < 1 ? 1 : price.getIntervalCount();
        String interval = price.getIntervalType() == null ? "month" : price.getIntervalType().toLowerCase();
        return switch (interval) {
            case "day" -> start.plus(count, ChronoUnit.DAYS);
            case "week" -> start.plus(count * 7L, ChronoUnit.DAYS);
            case "year" -> start.plus(count * 365L, ChronoUnit.DAYS);
            default -> start.plus(count * 30L, ChronoUnit.DAYS);
        };
    }

    private PremiumStatusResponse mapPremium(Subscription sub) {
        String planName = null;
        String currentPlan = FREE_PLAN;
        if (sub.getProductId() != null) {
            var product = productRepo.findById(sub.getProductId()).orElse(null);
            if (product != null) {
                planName = product.getName();
                currentPlan = planSlug(product);
            }
        }
        boolean paidPlan = BASIC_TRIP_PLAN.equals(currentPlan) || GROUP_PRO_TRIP_PLAN.equals(currentPlan);
        boolean active = isCurrentlyActive(sub);
        boolean expired = sub.getCurrentPeriodEnd() != null && !sub.getCurrentPeriodEnd().isAfter(Instant.now());
        return PremiumStatusResponse.builder()
                .premium(paidPlan && active)
                .subscriptionStatus(sub.getStatus())
                .activeSubscriptionId(sub.getId())
                .currentPlan(currentPlan)
                .planName(planName == null ? "Miễn phí" : planName)
                .features(PLAN_FEATURES.getOrDefault(currentPlan, PLAN_FEATURES.get(FREE_PLAN)))
                .startDate(sub.getCurrentPeriodStart())
                .endDate(sub.getCurrentPeriodEnd())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .daysRemaining(daysRemaining(sub.getCurrentPeriodEnd()))
                .expired(expired)
                .build();
    }

    private PricingPlanResponse mapPlan(Product p, List<Price> prices) {
        String slug = planSlug(p);
        return PricingPlanResponse.builder()
                .id(p.getId().toString()).stripeProductId(p.getStripeProductId())
                .slug(slug)
                .name(p.getName()).description(p.getDescription())
                .billingType(FREE_PLAN.equals(slug) ? "FREE_FOREVER" : "MONTHLY")
                .badge(BASIC_TRIP_PLAN.equals(slug) ? "Phổ biến" : GROUP_PRO_TRIP_PLAN.equals(slug) ? "Giá trị nhất" : null)
                .popular(BASIC_TRIP_PLAN.equals(slug))
                .bestValue(GROUP_PRO_TRIP_PLAN.equals(slug))
                .features(PLAN_FEATURES.getOrDefault(slug, List.of()))
                .prices(prices.stream().map(this::mapPrice).toList())
                .build();
    }

    private String planSlug(Product p) {
        return p == null || p.getStripeProductId() == null ? "" : p.getStripeProductId().trim().toUpperCase();
    }

    private PriceResponse mapPrice(Price price) {
        return PriceResponse.builder()
                .id(price.getId().toString()).stripePriceId(price.getStripePriceId())
                .amount(price.getAmount()).currency(price.getCurrency())
                .intervalType(price.getIntervalType()).intervalCount(price.getIntervalCount())
                .build();
    }

    private boolean isCurrentlyActive(Subscription sub) {
        if (sub == null || sub.getStatus() != SubStatus.ACTIVE) return false;
        Instant end = sub.getCurrentPeriodEnd();
        return end == null || end.isAfter(Instant.now());
    }

    private long daysRemaining(Instant endDate) {
        if (endDate == null) return 0;
        long seconds = Duration.between(Instant.now(), endDate).getSeconds();
        if (seconds <= 0) return 0;
        return (seconds + 86_399L) / 86_400L;
    }

    private Price resolveActivePrice(String priceId) {
        try {
            UUID uuid = UUID.fromString(priceId);
            Price price = priceRepo.findById(uuid)
                    .orElseThrow(() -> GolaException.notFound("Price"));
            if (!price.isActive()) {
                throw GolaException.badRequest("Price is not active");
            }
            return price;
        } catch (IllegalArgumentException e) {
            throw GolaException.badRequest("Invalid priceId");
        }
    }

    private SubscriptionResponse mapSubscription(Subscription s) {
        String productName = s.getProductId() == null ? null
                : productRepo.findById(s.getProductId()).map(Product::getName).orElse(null);
        return SubscriptionResponse.builder()
                .id(s.getId()).productId(s.getProductId()).priceId(s.getPriceId())
                .productName(productName).status(s.getStatus())
                .startDate(s.getCurrentPeriodStart())
                .endDate(s.getCurrentPeriodEnd())
                .currentPeriodStart(s.getCurrentPeriodStart())
                .currentPeriodEnd(s.getCurrentPeriodEnd())
                .daysRemaining(daysRemaining(s.getCurrentPeriodEnd()))
                .expired(s.getCurrentPeriodEnd() != null && !s.getCurrentPeriodEnd().isAfter(Instant.now()))
                .cancelAtPeriodEnd(s.isCancelAtPeriodEnd())
                .cancelledAt(s.getCancelledAt()).createdAt(s.getCreatedAt())
                .build();
    }

    private OrderResponse mapOrder(Order o) {
        return OrderResponse.builder()
                .id(o.getId()).priceId(o.getPriceId()).amount(o.getAmount())
                .currency(o.getCurrency()).status(o.getStatus())
                .stripeSessionId(o.getStripeSessionId())
                .paymentProvider(o.getPaymentProvider())
                .orderCode(o.getOrderCode())
                .transferContent(o.getTransferContent())
                .expiresAt(o.getExpiresAt())
                .paidAt(o.getPaidAt())
                .bankReferenceCode(o.getBankReferenceCode())
                .bankTransactionId(o.getBankTransactionId())
                .paidAmount(o.getPaidAmount())
                .autoConfirmedAt(o.getAutoConfirmedAt())
                .autoConfirmProvider(o.getAutoConfirmProvider())
                .createdAt(o.getCreatedAt()).updatedAt(o.getUpdatedAt())
                .build();
    }
}
