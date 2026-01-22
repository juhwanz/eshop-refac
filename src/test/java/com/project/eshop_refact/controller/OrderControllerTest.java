package com.project.eshop_refact.controller;

import com.project.eshop_refact.config.JwtUtil;
import com.project.eshop_refact.config.SecurityConfig;
import com.project.eshop_refact.config.UserDetailsImpl;
import com.project.eshop_refact.cotroller.OrderController;
import com.project.eshop_refact.domain.Order;
import com.project.eshop_refact.domain.OrderStatus;
import com.project.eshop_refact.domain.User;
import com.project.eshop_refact.domain.UserRoleEnum;
import com.project.eshop_refact.dto.OrderDto;
import com.project.eshop_refact.service.OrderService;
import com.project.eshop_refact.service.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean OrderService orderService;

    // 요 2놈 떄문에 에러 자꾸 뜸.
    @MockBean
    JwtUtil jwtUtil;
    @MockBean
    UserDetailsServiceImpl userDetailsServiceImpl;


    private UserDetailsImpl testUserDetails;

    @BeforeEach
    void setUp() {
        // Mock User 생성 (ID 필수)
        User user = new User("user@test.com", "pw", "user", UserRoleEnum.USER);
        ReflectionTestUtils.setField(user, "id", 1L);

        // 인증 객체 생성
        testUserDetails = new UserDetailsImpl(user);
    }

    @Test
    @DisplayName("주문 생성 API 성공 테스트")
    void createOrder() throws Exception {
        // given
        OrderDto.Request request = new OrderDto.Request();
        request.setProductId(100L);
        request.setCount(2);

        // Service Mocking: 주문 성공 시 주문 ID 500L 반환 가정
        given(orderService.order(eq(1L), eq(100L), eq(2))).willReturn(500L);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .with(csrf()) // CSRF 토큰 필요
                        .with(user(testUserDetails)) // 로그인한 유저 주입
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(500L)) // 반환된 ID 확인
                .andDo(print());
    }

    @Test
    @DisplayName("주문 목록 조회 API 성공 테스트")
    void getOrders() throws Exception {
        // given
        // 1. 가짜 Order 객체 생성 (빈 껍데기)
        Order order = new Order();

        // 2. Reflection을 사용해 필요한 필드 강제 주입 (Setter가 없으므로)
        ReflectionTestUtils.setField(order, "id", 10L);
        ReflectionTestUtils.setField(order, "orderDate", LocalDateTime.now());
        ReflectionTestUtils.setField(order, "orderItems", new ArrayList<>());
        ReflectionTestUtils.setField(order, "status", OrderStatus.ORDER); // [중요] null이면 DTO 변환 시 에러남!

        // 3. Service가 반환할 Page 객체 생성
        OrderDto.Response response = new OrderDto.Response(order);
        Page<OrderDto.Response> pageResponse = new PageImpl<>(List.of(response));

        // 4. Mocking: getOrders 호출 시 위에서 만든 pageResponse 반환
        given(orderService.getOrders(eq(1L), any())).willReturn(pageResponse);

        // when & then
        mockMvc.perform(get("/api/orders")
                        .with(user(testUserDetails))) // 인증 정보 주입
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(10L))
                .andExpect(jsonPath("$.content[0].orderStatus").value("ORDER"))
                .andDo(print());
    }
}