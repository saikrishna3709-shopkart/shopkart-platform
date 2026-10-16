package com.shopkart.storefront;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfiguration {

    @Bean
    RestClient storefrontRestClient(StorefrontProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.downstreamTimeout());
        requestFactory.setReadTimeout(properties.downstreamTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}