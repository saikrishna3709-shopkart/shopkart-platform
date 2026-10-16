package com.shopkart.order.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.shopkart.order.client.DownstreamClient;
import com.shopkart.order.client.DownstreamUnavailableException;
import com.shopkart.order.client.PaymentOutcome;
import com.shopkart.order.client.ProductInfo;
import com.shopkart.order.client.ReserveOutcome;
import com.shopkart.order.domain.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    private static final Map<String, Object> CART = Map.of(
            "customer_id", "cust-1",
            "items", List.of(Map.of("sku", "TSHIRT-001", "quantity", 2)),
            "card_token", "tok_ok",
            "email", "buyer@example.com",
            "phone", "+919876543210");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrderRepository repository;

    @MockitoBean private DownstreamClient downstream;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        reset(downstream);
        when(downstream.getProduct(anyString()))
                .thenAnswer(invocation -> Optional.of(new ProductInfo(invocation.getArgument(0), new BigDecimal("799.00"), true)));
        when(downstream.reserveStock(anyString(), anyString(), anyInt())).thenReturn(new ReserveOutcome(true, "OK"));
        when(downstream.charge(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new PaymentOutcome(true, "AUTHORIZED"));
        when(downstream.notify(anyString(), anyString(), anyString(), any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void healthDoesNotCallDownstream() throws Exception {
        mockMvc.perform(get("/healthz")).andExpect(status().isOk());
        verify(downstream, never()).getProduct(anyString());
    }

    @Test
    void successfulCheckoutConfirmsAndCommits() throws Exception {
        place("idem-key-0001", CART).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.total").value("1598.00"));
        verify(downstream).commitStock(anyString());
        verify(downstream, never()).releaseStock(anyString());
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(CART)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void doubleClickCreatesOnlyOneOrder() throws Exception {
        String first = orderId(place("same-key-123", CART).andReturn());
        String second = orderId(place("same-key-123", CART).andReturn());
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
        verify(downstream, times(1)).charge(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void differentKeysCreateDifferentOrders() throws Exception {
        String first = orderId(place("key-aaaa-1", CART).andReturn());
        String second = orderId(place("key-bbbb-2", CART).andReturn());
        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first);
    }

    @Test
    void unknownProductFailsWithoutReservation() throws Exception {
        when(downstream.getProduct(anyString())).thenReturn(Optional.empty());
        place("idem-key-0001", CART)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failure_reason").value("UNAVAILABLE:TSHIRT-001"));
        verify(downstream, never()).reserveStock(anyString(), anyString(), anyInt());
        verify(downstream, never()).charge(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void inactiveProductIsRejected() throws Exception {
        when(downstream.getProduct(anyString()))
                .thenReturn(Optional.of(new ProductInfo("TSHIRT-001", new BigDecimal("799.00"), false)));
        place("idem-key-0001", CART)
                .andExpect(jsonPath("$.failure_reason").value("UNAVAILABLE:TSHIRT-001"));
    }

    @Test
    void outOfStockFailsAndReleases() throws Exception {
        when(downstream.reserveStock(anyString(), anyString(), anyInt()))
                .thenReturn(new ReserveOutcome(false, "INSUFFICIENT_STOCK"));
        place("idem-key-0001", CART)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failure_reason").value("INSUFFICIENT_STOCK"));
        verify(downstream).releaseStock(anyString());
        verify(downstream, never()).charge(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void declinedPaymentReleasesStock() throws Exception {
        when(downstream.charge(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new PaymentOutcome(false, "CARD_DECLINED"));
        place("idem-key-0001", CART)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failure_reason").value("CARD_DECLINED"));
        verify(downstream).releaseStock(anyString());
        verify(downstream, never()).commitStock(anyString());
    }

    @Test
    void catalogOutageReturnsUnavailable() throws Exception {
        when(downstream.getProduct(anyString())).thenThrow(unavailable());
        place("idem-key-0001", CART).andExpect(status().isServiceUnavailable());
    }

    @Test
    void inventoryOutageReturnsUnavailableAndCompensates() throws Exception {
        when(downstream.reserveStock(anyString(), anyString(), anyInt())).thenThrow(unavailable());
        place("idem-key-0001", CART).andExpect(status().isServiceUnavailable());
        verify(downstream).releaseStock(anyString());
    }

    @Test
    void paymentOutageReturnsUnavailableAndCompensates() throws Exception {
        when(downstream.charge(anyString(), any(BigDecimal.class), anyString())).thenThrow(unavailable());
        place("idem-key-0001", CART).andExpect(status().isServiceUnavailable());
        verify(downstream).releaseStock(anyString());
    }

    @Test
    void retryAfterPaymentResponseFailureReusesSagaId() throws Exception {
        when(downstream.charge(anyString(), any(BigDecimal.class), anyString()))
                .thenThrow(unavailable())
                .thenReturn(new PaymentOutcome(true, "AUTHORIZED"));

        place("stable-key-01", CART).andExpect(status().isServiceUnavailable());
        place("stable-key-01", CART).andExpect(status().isCreated());

        ArgumentCaptor<String> orderIds = ArgumentCaptor.forClass(String.class);
        verify(downstream, times(2)).charge(orderIds.capture(), any(BigDecimal.class), anyString());
        org.assertj.core.api.Assertions.assertThat(orderIds.getAllValues().stream().distinct()).hasSize(1);
    }

    @Test
    void paidOrderSurvivesCommitFailure() throws Exception {
        doThrow(unavailable()).when(downstream).commitStock(anyString());
        place("idem-key-0001", CART).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void failedCompensationDoesNotBreakResponse() throws Exception {
        when(downstream.charge(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new PaymentOutcome(false, "CARD_DECLINED"));
        doThrow(unavailable()).when(downstream).releaseStock(anyString());
        place("idem-key-0001", CART).andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void multiItemOrderTotalsEveryLine() throws Exception {
        when(downstream.getProduct(anyString())).thenAnswer(invocation -> Optional.of(
                new ProductInfo(invocation.getArgument(0), new BigDecimal("100.00"), true)));
        Map<String, Object> cart = Map.of(
                "customer_id", "cust-1", "card_token", "tok_ok",
                "items", List.of(
                        Map.of("sku", "TSHIRT-001", "quantity", 2),
                        Map.of("sku", "SHOE-001", "quantity", 3)));
        place("multi-key-01", cart).andExpect(jsonPath("$.total").value("500.00"));
        verify(downstream).reserveStock(anyString(), eq("TSHIRT-001"), eq(2));
        verify(downstream).reserveStock(anyString(), eq("SHOE-001"), eq(3));
        verify(downstream).charge(anyString(), eq(new BigDecimal("500.00")), eq("tok_ok"));
    }

    @Test
    void secondLineOutOfStockReleasesFirst() throws Exception {
        when(downstream.reserveStock(anyString(), eq("A-001"), eq(1))).thenReturn(new ReserveOutcome(true, "OK"));
        when(downstream.reserveStock(anyString(), eq("B-002"), eq(1)))
                .thenReturn(new ReserveOutcome(false, "INSUFFICIENT_STOCK"));
        Map<String, Object> cart = Map.of(
                "customer_id", "cust-1", "card_token", "tok_ok",
                "items", List.of(Map.of("sku", "A-001", "quantity", 1), Map.of("sku", "B-002", "quantity", 1)));
        place("partial-key-1", cart).andExpect(jsonPath("$.status").value("FAILED"));
        verify(downstream).releaseStock(anyString());
    }

    @Test
    void orderCanBeRetrievedAndListed() throws Exception {
        String orderId = orderId(place("idem-key-0001", CART).andReturn());
        mockMvc.perform(get("/orders/{id}", orderId)).andExpect(status().isOk());
        mockMvc.perform(get("/orders").param("customer_id", "cust-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unknownOrderReturnsNotFound() throws Exception {
        mockMvc.perform(get("/orders/does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void confirmedOrderNotifiesCustomer() throws Exception {
        place("idem-key-0001", CART);
        verify(downstream).notify(anyString(), eq("cust-1"), eq("ORDER_CONFIRMED"),
                eq("buyer@example.com"), eq("+919876543210"), eq(new BigDecimal("1598.00")), any());
    }

    @Test
    void failedOrderAlsoNotifies() throws Exception {
        when(downstream.charge(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new PaymentOutcome(false, "CARD_DECLINED"));
        place("idem-key-0001", CART);
        verify(downstream).notify(anyString(), anyString(), eq("ORDER_FAILED"), any(), any(), any(), any());
    }

    @Test
    void notificationFailureDoesNotFailOrder() throws Exception {
        when(downstream.notify(anyString(), anyString(), anyString(), any(), any(), any(), any())).thenReturn(false);
        place("idem-key-0001", CART).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void customerWithoutContactIsNotNotified() throws Exception {
        Map<String, Object> cart = Map.of(
                "customer_id", "cust-1", "card_token", "tok_ok",
                "items", List.of(Map.of("sku", "A-1", "quantity", 1)));
        place("no-contact-01", cart);
        verify(downstream, never()).notify(anyString(), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void fullLifecycleNotifiesEachStep() throws Exception {
        String orderId = orderId(place("idem-key-0001", CART).andReturn());
        advance(orderId, "SHIPPED", "TRK900").andExpect(jsonPath("$.tracking_id").value("TRK900"));
        advance(orderId, "OUT_FOR_DELIVERY", null);
        advance(orderId, "DELIVERED", null).andExpect(jsonPath("$.status").value("DELIVERED"));

        ArgumentCaptor<String> events = ArgumentCaptor.forClass(String.class);
        verify(downstream, times(4)).notify(anyString(), anyString(), events.capture(), any(), any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(events.getAllValues()).containsExactly(
                "ORDER_CONFIRMED", "ORDER_SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED");
    }

    @Test
    void statusCannotSkipStep() throws Exception {
        String orderId = orderId(place("idem-key-0001", CART).andReturn());
        advance(orderId, "DELIVERED", null).andExpect(status().isConflict());
    }

    @Test
    void repeatedStatusDoesNotRenotify() throws Exception {
        String orderId = orderId(place("idem-key-0001", CART).andReturn());
        advance(orderId, "SHIPPED", null);
        advance(orderId, "SHIPPED", null);
        verify(downstream, times(2)).notify(anyString(), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void statusCannotMoveBackwards() throws Exception {
        String orderId = orderId(place("idem-key-0001", CART).andReturn());
        advance(orderId, "SHIPPED", null);
        advance(orderId, "CONFIRMED", null).andExpect(status().isConflict());
    }

    @Test
    void failedOrderCannotBeShipped() throws Exception {
        when(downstream.charge(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(new PaymentOutcome(false, "CARD_DECLINED"));
        String orderId = orderId(place("idem-key-0001", CART).andReturn());
        advance(orderId, "SHIPPED", null).andExpect(status().isConflict());
    }

    @Test
    void statusUpdateOnUnknownOrderReturnsNotFound() throws Exception {
        advance("nope", "SHIPPED", null).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions place(String key, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/orders")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private org.springframework.test.web.servlet.ResultActions advance(
            String orderId, String status, String trackingId) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("status", status);
        body.put("tracking_id", trackingId);
        return mockMvc.perform(patch("/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private String orderId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.order_id");
    }

    private DownstreamUnavailableException unavailable() {
        return new DownstreamUnavailableException("connection refused", new RuntimeException("down"));
    }
}