package com.shopkart.storefront.health;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {

    private final ObjectProvider<PrometheusMeterRegistry> registryProvider;

    public MetricsController(ObjectProvider<PrometheusMeterRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        PrometheusMeterRegistry registry = registryProvider.getIfAvailable();
        return registry == null ? "" : registry.scrape();
    }
}