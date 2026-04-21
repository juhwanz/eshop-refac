package com.project.eshop_refact.controller;

import com.project.eshop_refact.domain.order.*;
import com.project.eshop_refact.domain.queue.TestSupportController;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.global.security.JwtUtil;
import com.project.eshop_refact.global.security.SecurityConfig;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import com.project.eshop_refact.global.config.WebConfig;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.global.interceptor.QueueInterceptor;
import com.project.eshop_refact.global.security.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OrderController 웹 계층 슬라이스 테스트
 * Spring Security, 대기열 인터셉터(QueueInterceptor), 멱등성 검증 등 컨트롤러 진입 전후의 인프라적 제어를 포함하여 테스트합니다.
 */
@ActiveProfiles("test")
@WebMvcTest(controllers ={ OrderController.class, TestSupportController.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@Import({WebConfig.class, QueueInterceptor.class})
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OrderService orderService;
    @MockBean RedissonLockStockFacade redissonLockStockFacade;
    @MockBean
    WaitingQueueService waitingQueueService;

    @MockBean
    OrderIdempotencyService orderIdempotencyService;

    // @WebMvcTest 환경에서 로드되는 Security Filter Chain을 통과하기 위한 의존성 Mocking
    @MockBean
    JwtUtil jwtUtil;
    @MockBean
    UserDetailsServiceImpl userDetailsServiceImpl;
    @MockBean
    RedisTemplate<String, String> redisTemplate;

    private UserDetailsImpl testUserDetails;

    @BeforeEach
    void setUp() {
        // 테스트 간 격리를 위해 매번 새로운 인증 객체 생성
        User user = new User("user@test.com", "pw", "user", UserRoleEnum.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        testUserDetails = new UserDetailsImpl(user);

        // 기본적으로 대기열 검증을 통과하도록 설정
        given(waitingQueueService.isAllowed(any())).willReturn(true);
    }

    @Test
    @DisplayName("[실패] 대기열 토큰 없이 주문 요청 시 429 Too Many Requests 반환")
    void createOrder_Fail_When_Queue_Token_Invalid() throws Exception {
        // given
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setProductId(100L);
        request.setCount(1);

        given(waitingQueueService.isAllowed(anyLong())).willReturn(false);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUEUE_WAITING"))
                .andDo(print());
    }

    @Test
    @DisplayName("주문 생성 API 성공 테스트")
    void createOrder() throws Exception {
        // given
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setProductId(100L);
        request.setCount(2);

        OrderDto.CreateResponse responseDto = new OrderDto.CreateResponse(500L);
        given(orderIdempotencyService.processOrderWithIdempotency(anyString(), eq(1L), eq(100L), eq(2)))
                .willReturn(responseDto);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf()) // Spring Security 환경의 POST 요청 검증을 위한 CSRF 토큰 주입
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(500L)) // 반환된 ID 확인
                .andDo(print());
    }

    @Test
    @DisplayName(" 실패 : 주문 생성 : 수량이 0 이하일 경우 400 Bad Reques")
    void createOrder_fail() throws Exception{
        //given
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setProductId(100L);
        request.setCount(0);

        //when & Then
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("[실패] 주문 생성: 재고 부족 시 비즈니스 예외 발생")
    void createOrder_Fail_Out_Of_Stock() throws Exception {
        // given
        OrderDto.CreateRequest request = new OrderDto.CreateRequest();
        request.setProductId(100L);
        request.setCount(10); // 요청 수량

        // Mock 대상 수정 (Facade -> IdempotencyService)
        given(orderIdempotencyService.processOrderWithIdempotency(anyString(), anyLong(), anyLong(), anyInt()))
                .willThrow(new BusinessException(ErrorCode.OUT_OF_STOCK));
        // when & then
        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf())
                        .with(user(testUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // ErrorCode에 정의된 상태 코드에 맞게 변경
                .andDo(print());
    }
    @Test
    @DisplayName("주문 목록 조회 API 성공 테스트")
    void getOrders() throws Exception {
        // given
        Order order = new Order();

        ReflectionTestUtils.setField(order, "id", 10L);
        ReflectionTestUtils.setField(order, "status", OrderStatus.ORDER);

        // 순수 엔티티 상태(Mock)에서의 지연 로딩(N+1) 관련 예외를 방지하기 위해 명시적으로 빈 리스트 할당
        ReflectionTestUtils.setField(order, "orderItems", new ArrayList<>()); // N+1 방지용 빈 리스트

        Page<OrderDto.Response> pageResponse = new PageImpl<>(List.of(new OrderDto.Response(order)));

        given(orderService.getOrders(eq(1L), any())).willReturn(pageResponse);

        // when & then
        mockMvc.perform(get("/api/orders")
                        .with(user(testUserDetails)))// GET 요청은 CSRF 토큰 불필요 (조회니까)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderId").value(10L))
                .andExpect(jsonPath("$.data.content[0].orderStatus").value("ORDER"))
                .andDo(print());
    }

    @Test
    @DisplayName("대기열 토큰 발급 API 성공 테스트")
    void registerQueue() throws Exception {
        // given
        Long expectedRank = 15L;
        given(waitingQueueService.registerQueue(anyLong())).willReturn(expectedRank);

        // when & then
        mockMvc.perform(post("/api/orders/queue")
                        .with(csrf())
                        .with(user(testUserDetails)))
                .andExpect(status().isCreated()) // 201 Created 검증
                .andExpect(jsonPath("$.data").value(expectedRank.intValue()))
                .andDo(print());
    }
}