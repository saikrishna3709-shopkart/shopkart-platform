package com.shopkart.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;

import com.shopkart.order.config.ServiceProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpDownstreamClientTest {

    private MockRestServiceServer criticalServer;
    private MockRestServiceServer notificationServer;
    private HttpDownstreamClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder criticalBuilder = RestClient.builder();
        RestClient.Builder notificationBuilder = RestClient.builder();
        criticalServer = MockRestServiceServer.bindTo(criticalBuilder).build();
        notificationServer = MockRestServiceServer.bindTo(notificationBuilder).build();
        ServiceProperties properties = new ServiceProperties(
                "http://catalog", "http://inventory", "http://payment", "http://notification",
                Duration.ofSeconds(3), Duration.ofSeconds(2));
        client = new HttpDownstreamClient(criticalBuilder.build(), notificationBuilder.build(), properties);
    }

    @Test
    void parsesCatalogProduct() {
        criticalServer.expect(once(), requestTo("http://catalog/products/TSHIRT-001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"sku\":\"TSHIRT-001\",\"price\":\"799.00\",\"active\":true}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.getProduct("TSHIRT-001"))
                .contains(new ProductInfo("TSHIRT-001", new BigDecimal("799.00"), true));
        criticalServer.verify();
    }

    @Test
    void mapsCatalogNotFoundToEmpty() {
        criticalServer.expect(requestTo("http://catalog/products/missing"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.getProduct("missing")).isEmpty();
        criticalServer.verify();
    }

    @Test
    void mapsCatalogErrorsToDependencyFailure() {
        criticalServer.expect(requestTo("http://catalog/products/TSHIRT-001"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getProduct("TSHIRT-001"))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessageContaining("catalog:");
    }

    @Test
    void sendsInventoryContracts() {
        criticalServer.expect(requestTo("http://inventory/inventory/reserve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"order_id\":\"order-1\",\"sku\":\"TSHIRT-001\",\"quantity\":2}"))
                .andRespond(withSuccess("{\"reserved\":true,\"reason\":\"OK\"}", MediaType.APPLICATION_JSON));
        criticalServer.expect(requestTo("http://inventory/inventory/release"))
                .andExpect(content().json("{\"order_id\":\"order-1\"}"))
                .andRespond(withSuccess());
        criticalServer.expect(requestTo("http://inventory/inventory/commit"))
                .andExpect(content().json("{\"order_id\":\"order-1\"}"))
                .andRespond(withSuccess());

        assertThat(client.reserveStock("order-1", "TSHIRT-001", 2))
                .isEqualTo(new ReserveOutcome(true, "OK"));
        client.releaseStock("order-1");
        client.commitStock("order-1");
        criticalServer.verify();
    }

    @Test
    void mapsInventoryErrors() {
        criticalServer.expect(requestTo("http://inventory/inventory/reserve"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.reserveStock("order-1", "SKU-1", 1))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessageContaining("inventory:");
    }

    @Test
    void sendsDecimalPaymentAndParsesApproval() {
        criticalServer.expect(requestTo("http://payment/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"order_id\":\"order-1\",\"amount\":\"1598.00\",\"card_token\":\"tok_ok\"}"))
                .andRespond(withSuccess(
                        "{\"status\":\"APPROVED\",\"reason\":\"AUTHORIZED\"}", MediaType.APPLICATION_JSON));

        assertThat(client.charge("order-1", new BigDecimal("1598.00"), "tok_ok"))
                .isEqualTo(new PaymentOutcome(true, "AUTHORIZED"));
        criticalServer.verify();
    }

    @Test
    void mapsPaymentErrors() {
        criticalServer.expect(requestTo("http://payment/payments"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.charge("order-1", BigDecimal.TEN, "tok_ok"))
                .isInstanceOf(DownstreamUnavailableException.class)
                .hasMessageContaining("payment:");
    }

    @Test
    void skipsNotificationWithoutContactDetails() {
        assertThat(client.notify("order-1", "cust-1", "ORDER_CONFIRMED", null, null, null, null)).isFalse();
        notificationServer.verify();
    }

    @Test
    void sendsNotificationOptionalFields() {
        notificationServer.expect(requestTo("http://notification/notifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "order_id":"order-1",
                          "customer_id":"cust-1",
                          "event":"ORDER_SHIPPED",
                          "email":"buyer@example.com",
                          "phone":null,
                          "amount":"1598.00",
                          "tracking_id":"TRK-1"
                        }
                        """))
                .andRespond(withSuccess());

        assertThat(client.notify(
                "order-1", "cust-1", "ORDER_SHIPPED", "buyer@example.com", null,
                new BigDecimal("1598.00"), "TRK-1")).isTrue();
        notificationServer.verify();
    }

    @Test
    void notificationErrorsAreBestEffort() {
        notificationServer.expect(requestTo("http://notification/notifications"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(client.notify(
                "order-1", "cust-1", "ORDER_CONFIRMED", null, "+919876543210", null, null)).isFalse();
        notificationServer.verify();
    }
}