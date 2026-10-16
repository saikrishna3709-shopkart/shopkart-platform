package com.shopkart.inventory.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopkart.inventory.reservation.ReservationRepository;
import com.shopkart.inventory.stock.StockRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StockRepository stockRepository;
    @Autowired private ReservationRepository reservationRepository;

    @BeforeEach
    void cleanDatabase() {
        reservationRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Test
    void healthDoesNotUseDatabase() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void readinessAndMetricsAreExposed() throws Exception {
        mockMvc.perform(get("/readyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"));
        mockMvc.perform(get("/metrics")).andExpect(status().isOk());
    }

    @Test
    void createsAndReadsStock() throws Exception {
        stock("TSHIRT-001", 5).andExpect(status().isCreated());
        getStock().andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("TSHIRT-001"))
                .andExpect(jsonPath("$.available").value(5))
                .andExpect(jsonPath("$.reserved").value(0));
    }

    @Test
    void rejectsDuplicateSku() throws Exception {
        stock("TSHIRT-001", 5).andExpect(status().isCreated());
        stock("TSHIRT-001", 5).andExpect(status().isConflict());
    }

    @Test
    void unknownStockReturnsNotFound() throws Exception {
        mockMvc.perform(get("/inventory/MISSING-1"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/inventory/MISSING-1/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidQuantitiesAreRejected() throws Exception {
        stock("TSHIRT-001", -1).andExpect(status().isUnprocessableEntity());
        reserve("order-1", "TSHIRT-001", 0).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reservationMovesAvailableToReserved() throws Exception {
        stock("TSHIRT-001", 5);
        reserve("order-1", "TSHIRT-001", 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(true));
        getStock().andExpect(jsonPath("$.available").value(3))
                .andExpect(jsonPath("$.reserved").value(2));
    }

    @Test
    void refusesMoreThanAvailableWithoutChangingStock() throws Exception {
        stock("TSHIRT-001", 2);
        reserve("order-1", "TSHIRT-001", 3)
                .andExpect(jsonPath("$.reserved").value(false))
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_STOCK"));
        getStock().andExpect(jsonPath("$.available").value(2));
    }

    @Test
    void lastUnitGoesToExactlyOneBuyer() throws Exception {
        stock("TSHIRT-001", 1);
        reserve("order-1", "TSHIRT-001", 1).andExpect(jsonPath("$.reserved").value(true));
        reserve("order-2", "TSHIRT-001", 1)
                .andExpect(jsonPath("$.reserved").value(false))
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_STOCK"));
        getStock().andExpect(jsonPath("$.available").value(0));
    }

    @Test
    void reportsUnknownSku() throws Exception {
        reserve("order-1", "GHOST-999", 1)
                .andExpect(jsonPath("$.reserved").value(false))
                .andExpect(jsonPath("$.reason").value("SKU_NOT_STOCKED"));
    }

    @Test
    void retryDoesNotDoubleHold() throws Exception {
        stock("TSHIRT-001", 5);
        reserve("order-1", "TSHIRT-001", 2);
        reserve("order-1", "TSHIRT-001", 2)
                .andExpect(jsonPath("$.reason").value("ALREADY_HELD"));
        getStock().andExpect(jsonPath("$.reserved").value(2));
    }

    @Test
    void releaseReturnsStock() throws Exception {
        stock("TSHIRT-001", 5);
        reserve("order-1", "TSHIRT-001", 2);
        action("release", "order-1").andExpect(jsonPath("$[0].status").value("RELEASED"));
        getStock().andExpect(jsonPath("$.available").value(5))
                .andExpect(jsonPath("$.reserved").value(0));
    }

    @Test
    void releaseIsIdempotent() throws Exception {
        stock("TSHIRT-001", 5);
        reserve("order-1", "TSHIRT-001", 2);
        action("release", "order-1");
        action("release", "order-1");
        getStock().andExpect(jsonPath("$.available").value(5));
    }

    @Test
    void commitFinalizesSale() throws Exception {
        stock("TSHIRT-001", 5);
        reserve("order-1", "TSHIRT-001", 2);
        action("commit", "order-1").andExpect(jsonPath("$[0].status").value("COMMITTED"));
        getStock().andExpect(jsonPath("$.available").value(3))
                .andExpect(jsonPath("$.reserved").value(0));
    }

    @Test
    void committedOrderCannotBeReleased() throws Exception {
        stock("TSHIRT-001", 5);
        reserve("order-1", "TSHIRT-001", 2);
        action("commit", "order-1");
        action("release", "order-1");
        getStock().andExpect(jsonPath("$.available").value(3));
    }

    @Test
    void restockIncreasesAvailability() throws Exception {
        stock("TSHIRT-001", 1);
        mockMvc.perform(patch("/inventory/TSHIRT-001/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(10));
    }

    @Test
    void releasedStockCanBeReservedAgain() throws Exception {
        stock("TSHIRT-001", 1);
        reserve("order-1", "TSHIRT-001", 1);
        reserve("order-2", "TSHIRT-001", 1).andExpect(jsonPath("$.reserved").value(false));
        action("release", "order-1");
        reserve("order-2", "TSHIRT-001", 1).andExpect(jsonPath("$.reserved").value(true));
    }

    private org.springframework.test.web.servlet.ResultActions stock(String sku, int quantity) throws Exception {
        return mockMvc.perform(post("/inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"%s\",\"quantity\":%d}".formatted(sku, quantity)));
    }

    private org.springframework.test.web.servlet.ResultActions reserve(
            String orderId, String sku, int quantity) throws Exception {
        return mockMvc.perform(post("/inventory/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"order_id\":\"%s\",\"sku\":\"%s\",\"quantity\":%d}"
                        .formatted(orderId, sku, quantity)));
    }

    private org.springframework.test.web.servlet.ResultActions action(String action, String orderId) throws Exception {
        return mockMvc.perform(post("/inventory/" + action)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"order_id\":\"%s\"}".formatted(orderId)));
    }

    private org.springframework.test.web.servlet.ResultActions getStock() throws Exception {
        return mockMvc.perform(get("/inventory/TSHIRT-001"));
    }
}