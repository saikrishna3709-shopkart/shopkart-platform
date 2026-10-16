package com.shopkart.notification.service;

public class NoRecipientException extends RuntimeException {

    public NoRecipientException() {
        super("no email or phone supplied");
    }
}