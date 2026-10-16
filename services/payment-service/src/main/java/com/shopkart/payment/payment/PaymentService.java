package com.shopkart.payment.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Map<String, String> DECLINE_TOKENS = Map.of(
            "tok_decline", "CARD_DECLINED",
            "tok_expired", "CARD_EXPIRED");

    private final PaymentRepository repository;
    private final PaymentCreator creator;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final BigDecimal declineAboveAmount;

    public PaymentService(
            PaymentRepository repository,
            PaymentCreator creator,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Value("${payment.decline-above-amount}") BigDecimal declineAboveAmount) {
        this.repository = repository;
        this.creator = creator;
        this.meterRegistryProvider = meterRegistryProvider;
        this.declineAboveAmount = declineAboveAmount;
    }

    public PaymentResponse charge(ChargeRequest request) {
        Payment existing = repository.findByOrderId(request.orderId()).orElse(null);
        if (existing != null) {
            increment("IDEMPOTENT_REPLAY");
            return PaymentResponse.from(existing);
        }

        Authorization authorization = authorize(request.amount(), request.cardToken());
        Payment payment = new Payment(
                UUID.randomUUID().toString(),
                request.orderId(),
                request.amount(),
                authorization.status(),
                authorization.reason());
        try {
            Payment saved = creator.create(payment);
            increment(saved.getStatus().name());
            return PaymentResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            Payment replay = repository.findByOrderId(request.orderId()).orElseThrow(() -> exception);
            increment("IDEMPOTENT_REPLAY");
            return PaymentResponse.from(replay);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(String orderId) {
        return repository.findByOrderId(orderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    private Authorization authorize(BigDecimal amount, String cardToken) {
        String tokenReason = DECLINE_TOKENS.get(cardToken);
        if (tokenReason != null) {
            return new Authorization(PaymentStatus.DECLINED, tokenReason);
        }
        if (amount.compareTo(declineAboveAmount) > 0) {
            return new Authorization(PaymentStatus.DECLINED, "LIMIT_EXCEEDED");
        }
        return new Authorization(PaymentStatus.APPROVED, "AUTHORIZED");
    }

    private void increment(String outcome) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter("shopkart.charges", "outcome", outcome).increment();
        }
    }

    private record Authorization(PaymentStatus status, String reason) {
    }
}