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

// SecurityConfig는 배제.
@WebMvcTest(controllers = ProductController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
class ProductControllerTest {

    // [가짜 HTTP 요청 도구] : WAS 띄우지 않고, GET,POST같은 요청을 흉내내주는 스프링 테스트 객체
    @Autowired MockMvc mockMvc;
    // [JSON 변환 도구 ] : DTO -> JSON 직렬화 , JSON -> DTO 역직렬화 역할.
    @Autowired ObjectMapper objectMapper;
    // [ Mock 주입]
    @MockBean ProductService productService;

    // [컨텍스트 로드 에러 방지용 껍데기]
    // 요 2놈 떄문에 에러 자꾸 뜸. -> 웹 관련 Bean들을 띄울 때, 기본적으로 필터 체인, 다른 인터셉트 동작
    // -> 빈 껍데기라도 던져줘야 함. ????
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
    // [가짜 사용자] : SecurityConfig를 껏지만, 여전히 메서드 보안 권한 필요 할 수도 -> 가짜.
    @WithMockUser
    void registerProduct() throws Exception {
        // given
        // @RequsetBody를 통과하기 위함.
        ProductDto.RegisterRequest request = new ProductDto.RegisterRequest();
        request.setName("New Item");
        request.setPrice(1000);
        request.setStockQuantity(100);

        // [Stubbing (가짜 객체 행동 정의)] : Mockito 라이브러리 기능
        // -> 아무 값이나 들어와도 1L 반환. : service 우회법
        given(productService.registerProduct(any())).willReturn(1L);

        // when & then
        mockMvc.perform(post("/api/products")
                        // 스프링 시큐리티 기본 요구 : CSRF 해킹 방어 토큰 => 403 에러 발생.
                        .with(csrf())
                        .characterEncoding("UTF-8")
                        // JSON 형식이야 - 헤더
                        .contentType(MediaType.APPLICATION_JSON)
                        // 직렬화 - 바디

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

        //PathVariable로 넘어온 productId를 -> 서비스로 넘김
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
        ReflectionTestUtils.setField(product, "id", 100L); // id = 100L

        ProductDto.Response responseDto = new ProductDto.Response(product);
        Page<ProductDto.Response> pageResponse = new PageImpl<>(List.of(responseDto));

        //[Stubbing] : /search 호출 시 pageResponse를 리턴해라.
        given(productService.search(any(), any())).willReturn(pageResponse);

        // when & then
        // get 요청 시에는 csrf 방어 필요 X -> .with(csrf()) 생략.
        mockMvc.perform(get("/api/products/search")
                        // url 뒤 파라미터 (?name=MacBook&minPrice=100000)
                        .param("name", "MacBook")
                        .param("minPrice", "1000000")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk()) // 200??
                // & - JSON의 최상위 루트
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

        //Slice
        Slice<ProductDto.Response> slice = new org.springframework.data.domain.SliceImpl<>(List.of(responseDto));

        //any
        given(productService.searchNoOffset(any(),any(), any())).willReturn(slice);

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
        Product product = new Product("MacBook Pro", newPrice, 5); // 가격이 수정된 상태 가정
        ReflectionTestUtils.setField(product, "id", productId);
        ProductDto.Response responseDto = new ProductDto.Response(product);

        given(productService.updateProductPrice(productId, newPrice)).willReturn(responseDto);

        ProductDto.PriceUpdateRequest request = new ProductDto.PriceUpdateRequest();
        ReflectionTestUtils.setField(request, "newPrice", newPrice);
        // when & then
        // PATCH 요청, 데이터 변경이 일어나므로 POST와 마찬가지로 CSRF 토큰 필요
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