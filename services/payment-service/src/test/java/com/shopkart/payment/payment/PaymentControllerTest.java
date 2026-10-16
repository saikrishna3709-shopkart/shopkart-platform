package com.shopkart.payment.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void approvesValidCard() throws Exception {
        charge("order-1", "1598.00", "tok_ok")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reason").value("AUTHORIZED"));
    }

    @Test
    void reportsDeclinedCard() throws Exception {
        charge("order-1", "1598.00", "tok_decline")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"))
                .andExpect(jsonPath("$.reason").value("CARD_DECLINED"));
    }

    @Test
    void declinesAmountAboveLimit() throws Exception {
        charge("order-1", "250000.00", "tok_ok")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("LIMIT_EXCEEDED"));
    }

    @Test
    void retryReturnsOriginalPayment() throws Exception {
        MvcResult first = charge("order-1", "1598.00", "tok_ok")
                .andExpect(status().isCreated())
                .andReturn();
        String paymentId = com.jayway.jsonpath.JsonPath.read(
                first.getResponse().getContentAsString(), "$.payment_id");

        charge("order-1", "1598.00", "tok_ok")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment_id").value(paymentId));
    }

    @Test
    void replayIgnoresChangedAmount() throws Exception {
        charge("order-1", "1598.00", "tok_ok").andExpect(status().isCreated());

        charge("order-1", "999999.00", "tok_ok")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("1598.00"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        charge("order-1", "-1", "tok_ok")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void looksUpPaymentByOrder() throws Exception {
        charge("order-9", "1598.00", "tok_ok").andExpect(status().isCreated());

        mockMvc.perform(get("/payments/order-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order_id").value("order-9"));
    }

    @Test
    void returnsNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(get("/payments/nope"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions charge(
            String orderId, String amount, String cardToken) throws Exception {
        String body = """
                {"order_id":"%s","amount":"%s","card_token":"%s"}
                """.formatted(orderId, amount, cardToken);
        return mockMvc.perform(post("/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}