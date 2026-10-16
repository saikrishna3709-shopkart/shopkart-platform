package com.shopkart.payment.payment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentCreator {

    private final PaymentRepository repository;

    public PaymentCreator(PaymentRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment create(Payment payment) {
        return repository.saveAndFlush(payment);
    }
}