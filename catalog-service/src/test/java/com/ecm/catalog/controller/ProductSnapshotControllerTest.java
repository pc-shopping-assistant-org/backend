package com.ecm.catalog.controller;

import com.ecm.catalog.dto.response.CursorPageResponse;
import com.ecm.catalog.dto.response.ProductDetailResponse;
import com.ecm.catalog.service.ProductQueryService;
import com.ecm.catalog.service.ProductService;
import com.ecm.catalog.service.ProductVariantService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductSnapshotControllerTest {
    @Test
    void snapshotRouteIsNotParsedAsProductUuidAndUsesStandardEnvelope() throws Exception {
        ProductQueryService query = mock(ProductQueryService.class);
        UUID id = UUID.randomUUID();
        when(query.getProductSnapshots(any())).thenReturn(CursorPageResponse.<ProductDetailResponse>builder()
                .items(List.of(ProductDetailResponse.builder().id(id).name("CPU").status("ACTIVE").build()))
                .hasNext(false).size(1).build());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductController(
                mock(ProductService.class), mock(ProductVariantService.class), query)).build();

        mvc.perform(get("/products/snapshot").param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.data.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.data.hasNext").value(false));
        verify(query).getProductSnapshots(any());
    }
}
