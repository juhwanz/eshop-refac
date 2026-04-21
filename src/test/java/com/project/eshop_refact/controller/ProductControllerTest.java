package com.project.eshop_refact.controller;

import com.project.eshop_refact.domain.product.ProductController;
import com.project.eshop_refact.global.security.JwtUtil;
import com.project.eshop_refact.global.security.SecurityConfig;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductDto;
import com.project.eshop_refact.domain.product.ProductService;
import com.project.eshop_refact.global.security.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.RedisTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/**
 * ProductController 웹 계층 슬라이스 테스트
 * SecurityConfig를 배제하고 컨트롤러 로직(요청 매핑, 파라미터 바인딩, 응답 포맷 검증)에 집중하여 테스트합니다.
 */
@WebMvcTest(controllers = ProductController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ProductService productService;

    // WebMvcTest 환경에서 Security Filter Chain 및 Interceptor 통과를 위한 Mocking
    @MockBean
    JwtUtil jwtUtil;
    @MockBean
    UserDetailsServiceImpl userDetailsServiceImpl;
    @MockBean
    WaitingQueueService waitingQueueService;
    @MockBean
    RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("상품 등록 API - 201 Created 반환 검증")
    @WithMockUser // 컨트롤러 메서드의 권한 검증(hasRole 등) 통과를 위한 Mock 인증 객체 주입
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
                        .characterEncoding("UTF-8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("상품등록 성공"))
                .andExpect(jsonPath("$.data").value(1L))
                .andDo(print());
    }

    @Test
    @DisplayName("단일 상품 조회 API")
    @WithMockUser
    void getProductById() throws Exception{
        //Given
        Long productId = 1L;
        Product product = new Product("MAC", 250000, 5);
        ReflectionTestUtils.setField(product, "id", productId);
        ProductDto.Response responseDto = new ProductDto.Response(product);

        given(productService.getProductById(productId)).willReturn(responseDto);

        //When & Then
        mockMvc.perform(get("/api/products/{productId}",productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("MAC"))
                .andExpect(jsonPath("$.data.price").value(250000))
                .andDo(print());
    }

    @Test
    @DisplayName("상품 검색 API")
    @WithMockUser
    void searchProducts() throws Exception {
        // given
        Product product = new Product("MacBook", 2000000, 10);
        ReflectionTestUtils.setField(product, "id", 100L);

        ProductDto.Response responseDto = new ProductDto.Response(product);
        Page<ProductDto.Response> pageResponse = new PageImpl<>(List.of(responseDto));

        given(productService.search(any(), any())).willReturn(pageResponse);

        mockMvc.perform(get("/api/products/search")
                        .param("name", "MacBook")
                        .param("minPrice", "1000000")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("MacBook"))
                .andExpect(jsonPath("$.data.content[0].price").value(2000000))
                .andDo(print());
    }

    @Test
    @DisplayName("상품 검색 API (No-offset/Slice)")
    @WithMockUser
    void searchNoOffset()throws Exception{
        //Given
        Product product = new Product("Phone",20000,20);
        ReflectionTestUtils.setField(product, "id", 50L);
        ProductDto.Response responseDto = new ProductDto.Response(product);

        Slice<ProductDto.Response> slice = new org.springframework.data.domain.SliceImpl<>(List.of(responseDto));

        given(productService.searchNoOffset(any(),any(), any())).willReturn(slice);

        // When & Then
        mockMvc.perform(get("/api/products/search/no-offset")
                    .param("lastProductId", "49")
                    .param("name", "Phone")
                    .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("Phone"))
                .andExpect(jsonPath("$.data.content[0].price").value(20000))
                .andDo(print());
    }

    @Test
    @DisplayName("상품 가격 수정 API (PatchMapping)")
    @WithMockUser
    void updateProductPrice() throws Exception {
        // given
        Long productId = 1L;
        int newPrice = 2800000;
        Product product = new Product("MacBook Pro", newPrice, 5);
        ReflectionTestUtils.setField(product, "id", productId);
        ProductDto.Response responseDto = new ProductDto.Response(product);

        given(productService.updateProductPrice(productId, newPrice)).willReturn(responseDto);

        ProductDto.PriceUpdateRequest request = new ProductDto.PriceUpdateRequest();
        ReflectionTestUtils.setField(request, "newPrice", newPrice);

        // when & then
        mockMvc.perform(patch("/api/products/{productId}/price", productId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // ApiResponse 객체 구조(message, data)에 맞춰 jsonPath 검증
                .andExpect(jsonPath("$.message").value("가격수정 성공"))
                .andExpect(jsonPath("$.data.price").value(newPrice))
                .andDo(print());
    }
}