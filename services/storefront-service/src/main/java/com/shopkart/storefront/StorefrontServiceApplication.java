package com.shopkart.storefront;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorefrontProperties.class)
public class StorefrontServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StorefrontServiceApplication.class, args);
    }
}