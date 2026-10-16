package com.shopkart.catalog.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    private static final String PRODUCT = """
            {
              "sku": "TSHIRT-001",
              "name": "Cotton Round Neck T-Shirt",
              "description": "Regular fit, 100% cotton",
              "category": "clothing",
              "brand": "Nova",
              "price": "799.00"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsAndFetchesProduct() throws Exception {
        create(PRODUCT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.price").value("799.00"));

        mockMvc.perform(get("/products/TSHIRT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cotton Round Neck T-Shirt"));
    }

    @Test
    void rejectsDuplicateSku() throws Exception {
        create(PRODUCT).andExpect(status().isCreated());
        create(PRODUCT).andExpect(status().isConflict());
    }

    @Test
    void returnsNotFoundForUnknownSku() throws Exception {
        mockMvc.perform(get("/products/NOPE-000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsZeroAndNegativePrices() throws Exception {
        create(PRODUCT.replace("799.00", "0"))
                .andExpect(status().isUnprocessableEntity());
        create(PRODUCT.replace("TSHIRT-001", "X-2").replace("799.00", "-10"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void filtersProductsByCategory() throws Exception {
        create(PRODUCT).andExpect(status().isCreated());
        create(PRODUCT
                .replace("TSHIRT-001", "SHOE-001")
                .replace("clothing", "footwear"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/products").param("category", "footwear"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sku").value("SHOE-001"));
    }

    @Test
    void updatesPrice() throws Exception {
        create(PRODUCT).andExpect(status().isCreated());

        mockMvc.perform(patch("/products/TSHIRT-001/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":\"649.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value("649.00"));
    }

    @Test
    void softDeletedProductIsHiddenButReadable() throws Exception {
        create(PRODUCT).andExpect(status().isCreated());

        mockMvc.perform(delete("/products/TSHIRT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/products/TSHIRT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    private org.springframework.test.web.servlet.ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}