package com.shopkart.order.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {

    @Bean
    @Qualifier("criticalRestClient")
    RestClient criticalRestClient(ServiceProperties properties) {
        return build(properties.downstreamTimeout());
    }

    @Bean
    @Qualifier("notificationRestClient")
    RestClient notificationRestClient(ServiceProperties properties) {
        return build(properties.notificationTimeout());
    }

    private RestClient build(java.time.Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}