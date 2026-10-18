package com.shopkart.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.shopkart.notification.notification.NotificationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotificationRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void healthDoesNotUseDatabase() throws Exception {
        mockMvc.perform(get("/healthz")).andExpect(status().isOk());
    }

    @Test
    void confirmationGoesToBothChannels() throws Exception {
        List<Map<String, Object>> sent = notify("ORDER_CONFIRMED", "order-1", "buyer@example.com", "+919876543210",
                Map.of("amount", "1598.00"));
        assertThat(sent).extracting(item -> item.get("channel")).containsExactlyInAnyOrder("EMAIL", "SMS");
        assertThat(sent).allMatch(item -> "SENT".equals(item.get("status")));
    }

    @Test
    void wordingMatchesEvent() throws Exception {
        String shipped = body(notify("ORDER_SHIPPED", "order-1", "buyer@example.com", "+919876543210",
                Map.of("tracking_id", "TRK123")));
        assertThat(shipped).contains("on the way", "TRK123");
        assertThat(body(notify("OUT_FOR_DELIVERY", "order-2", "buyer@example.com", null, Map.of())))
                .contains("out for delivery");
        assertThat(body(notify("DELIVERED", "order-3", "buyer@example.com", null, Map.of())))
                .contains("delivered");
    }

    @Test
    void amountOnlyAppearsWhenSupplied() throws Exception {
        assertThat(body(notify("ORDER_CONFIRMED", "order-1", "buyer@example.com", null,
                Map.of("amount", "1598.00")))).contains("Rs 1598.00");
        assertThat(body(notify("ORDER_CONFIRMED", "order-2", "buyer@example.com", null, Map.of())))
                .doesNotContain("Rs");
    }

    @Test
    void repeatedEventDoesNotSendTwice() throws Exception {
        List<Map<String, Object>> first = notify("ORDER_CONFIRMED", "order-1", "buyer@example.com", "+919876543210", Map.of());
        List<Map<String, Object>> replay = notify("ORDER_CONFIRMED", "order-1", "buyer@example.com", "+919876543210", Map.of());
        assertThat(replay).extracting(item -> item.get("notification_id"))
                .containsExactlyElementsOf(first.stream().map(item -> item.get("notification_id")).toList());
        list("order_id", "order-1").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void differentEventsAreAllSent() throws Exception {
        notify("ORDER_CONFIRMED", "order-1", "buyer@example.com", "+919876543210", Map.of());
        notify("ORDER_SHIPPED", "order-1", "buyer@example.com", "+919876543210", Map.of());
        notify("DELIVERED", "order-1", "buyer@example.com", "+919876543210", Map.of());
        list("order_id", "order-1").andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void smsOnlyCreatesOneMessage() throws Exception {
        List<Map<String, Object>> sent = notify("ORDER_CONFIRMED", "order-1", null, "+919876543210", Map.of());
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().get("channel")).isEqualTo("SMS");
    }

    @Test
    void requestWithoutContactIsRejected() throws Exception {
        notifyRequest("ORDER_CONFIRMED", "order-1", null, null, Map.of())
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void failedDeliveryIsRecordedNotRaised() throws Exception {
        List<Map<String, Object>> sent = notify("ORDER_CONFIRMED", "order-1", "bounce@example.com", "+919876543210", Map.of());
        Map<String, Object> email = channel(sent, "EMAIL");
        Map<String, Object> sms = channel(sent, "SMS");
        assertThat(email.get("status")).isEqualTo("FAILED");
        assertThat(email.get("last_error")).isNotEqualTo("");
        assertThat(sms.get("status")).isEqualTo("SENT");
    }

    @Test
    void invalidPhoneOnlyFailsSms() throws Exception {
        List<Map<String, Object>> sent = notify("ORDER_CONFIRMED", "order-1", "buyer@example.com", "not-a-number", Map.of());
        assertThat(channel(sent, "SMS").get("status")).isEqualTo("FAILED");
        assertThat(channel(sent, "EMAIL").get("status")).isEqualTo("SENT");
    }

    @Test
    void retryLeavesDeliveredMessagesAlone() throws Exception {
        notify("ORDER_CONFIRMED", "order-1", "buyer@example.com", "+919876543210", Map.of());
        retry().andExpect(jsonPath("$.retried").value(0))
                .andExpect(jsonPath("$.delivered").value(0))
                .andExpect(jsonPath("$.abandoned").value(0));
    }

    @Test
    void retryAbandonsAfterMaximumAttempts() throws Exception {
        notify("ORDER_CONFIRMED", "order-1", "bounce@example.com", "+919876543210", Map.of());
        retry();
        retry();
        retry().andExpect(jsonPath("$.abandoned").value(1));
        list("order_id", "order-1")
                .andExpect(jsonPath("$[?(@.channel == 'EMAIL')].status").value("ABANDONED"))
                .andExpect(jsonPath("$[?(@.channel == 'EMAIL')].attempts").value(4));
    }

    @Test
    void abandonedMessagesAreNotRetried() throws Exception {
        notify("ORDER_CONFIRMED", "order-1", "bounce@example.com", "+919876543210", Map.of());
        retry(); retry(); retry();
        retry().andExpect(jsonPath("$.retried").value(0));
    }

    @Test
    void listsByCustomer() throws Exception {
        notify("ORDER_CONFIRMED", "order-1", "buyer@example.com", "+919876543210", Map.of());
        list("customer_id", "cust-1").andExpect(jsonPath("$.length()").value(2));
    }

    private List<Map<String, Object>> notify(
            String event, String orderId, String email, String phone, Map<String, Object> extra) throws Exception {
        MvcResult result = notifyRequest(event, orderId, email, phone, extra)
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<>() {});
    }

    private org.springframework.test.web.servlet.ResultActions notifyRequest(
            String event, String orderId, String email, String phone, Map<String, Object> extra) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("order_id", orderId);
        request.put("customer_id", "cust-1");
        request.put("event", event);
        request.put("email", email);
        request.put("phone", phone);
        request.putAll(extra);
        return mockMvc.perform(post("/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)));
    }

    private org.springframework.test.web.servlet.ResultActions retry() throws Exception {
        return mockMvc.perform(post("/notifications/retry")).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions list(String parameter, String value) throws Exception {
        return mockMvc.perform(get("/notifications").param(parameter, value)).andExpect(status().isOk());
    }

    private String body(List<Map<String, Object>> notifications) {
        return (String) notifications.getFirst().get("body");
    }

    private Map<String, Object> channel(List<Map<String, Object>> notifications, String channel) {
        return notifications.stream().filter(item -> channel.equals(item.get("channel"))).findFirst().orElseThrow();
    }
}