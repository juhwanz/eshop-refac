package com.project.eshop_refact.controller;

import com.project.eshop_refact.config.JwtUtil;
import com.project.eshop_refact.config.SecurityConfig;
import com.project.eshop_refact.domain.Product;
import com.project.eshop_refact.dto.ProductDto;
import com.project.eshop_refact.service.ProductService;
import com.project.eshop_refact.service.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ProductService productService;

    // 요 2놈 떄문에 에러 자꾸 뜸.
    @MockBean
    JwtUtil jwtUtil;
    @MockBean
    UserDetailsServiceImpl userDetailsServiceImpl;

    @Test
    @DisplayName("상품 등록 API")
    @WithMockUser
    void registerProduct() throws Exception {
        // given
        ProductDto.RegisterRequest request = new ProductDto.RegisterRequest();
        request.setName("New Item");
        request.setPrice(1000);
        request.setStockQuantity(100);

        given(productService.registerProduct(any())).willReturn(1L);

        // when & then
        mockMvc.perform(post("/api/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1L))
                .andDo(print());
    }

    @Test
    @DisplayName("상품 검색 API")
    @WithMockUser
    void searchProducts() throws Exception {
        // given
        Product product = new Product("MacBook", 2000000, 10);
        ReflectionTestUtils.setField(product, "id", 100L); // ID 주입

        ProductDto.Response responseDto = new ProductDto.Response(product);
        Page<ProductDto.Response> pageResponse = new PageImpl<>(List.of(responseDto));

        // Controller는 파라미터를 객체(SearchCondition)로 받으므로 any() 사용
        given(productService.search(any(), any())).willReturn(pageResponse);

        // when & then
        mockMvc.perform(get("/api/products/search")
                        .param("name", "MacBook")
                        .param("minPrice", "1000000")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("MacBook"))
                .andExpect(jsonPath("$.content[0].price").value(2000000))
                .andDo(print());
    }
}