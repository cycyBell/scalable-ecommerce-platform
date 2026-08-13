package com.rtxnano.ecommerce.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * ==============================================================================
 * CONFIGURATION: Inter-Service RestClient Config
 * ==============================================================================
 * Configures modern Spring 6 RestClient beans for Cart Service and Product Catalog Service,
 * applying connection timeouts, read timeouts, and standardized HTTP headers.
 */
@Configuration
public class RestClientConfig {

    @Value("${app.services.cart.url:http://localhost:8002}")
    private String cartBaseUrl;

    @Value("${app.services.cart.connect-timeout-ms:3000}")
    private int cartConnectTimeout;

    @Value("${app.services.cart.read-timeout-ms:5000}")
    private int cartReadTimeout;

    @Value("${app.services.catalog.url:http://localhost:8000}")
    private String catalogBaseUrl;

    @Value("${app.services.catalog.connect-timeout-ms:3000}")
    private int catalogConnectTimeout;

    @Value("${app.services.catalog.read-timeout-ms:5000}")
    private int catalogReadTimeout;

    @Bean
    public RestClient cartRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(cartConnectTimeout));
        factory.setReadTimeout(Duration.ofMillis(cartReadTimeout));

        return RestClient.builder()
                .baseUrl(cartBaseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "order-service/1.0")
                .build();
    }

    @Bean
    public RestClient catalogRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(catalogConnectTimeout));
        factory.setReadTimeout(Duration.ofMillis(catalogReadTimeout));

        return RestClient.builder()
                .baseUrl(catalogBaseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "order-service/1.0")
                .build();
    }
}
