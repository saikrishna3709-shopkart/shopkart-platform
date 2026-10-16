package com.shopkart.notification.delivery;

import org.springframework.stereotype.Component;

@Component
public class DeliveryProvider {

    public void send(Channel channel, String recipient, String body) {
        if (recipient == null || recipient.isBlank()) {
            throw new DeliveryException("no recipient address");
        }
        if (recipient.toLowerCase().contains("bounce")) {
            throw new DeliveryException("recipient rejected by provider");
        }
        if (channel == Channel.SMS && !recipient.replace("+", "").chars().allMatch(Character::isDigit)) {
            throw new DeliveryException("invalid phone number");
        }
    }
}