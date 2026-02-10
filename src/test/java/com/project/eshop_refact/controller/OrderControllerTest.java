package com.project.eshop_refact.controller;

import com.project.eshop_refact.config.JwtUtil;
import com.project.eshop_refact.config.SecurityConfig;
import com.project.eshop_refact.config.UserDetailsImpl;
import com.project.eshop_refact.config.WebConfig;
import com.project.eshop_refact.domain.Order;
import com.project.eshop_refact.domain.OrderStatus;
import com.project.eshop_refact.domain.User;
import com.project.eshop_refact.domain.UserRoleEnum;
import com.project.eshop_refact.dto.OrderDto;
import com.project.eshop_refact.facade.RedissonLockStockFacade;
import com.project.eshop_refact.interceptor.QueueInterceptor;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMVcTest Vs @SpringBootTest
// -> 컨트롤러 계층만 잘라서 띄움. -> DB까지 띄우지 않고, Service이하는 전부
// 가짜객체 (Mock)로 대체 -> Service, Repository는 @MockBean으로 채워야 함.
// "왜 WebMVCTest? -> API 계층의 유효성 검증 + HTTP 응답만 빠르게 테스트하기 위해서.
@WebMvcTest(controllers = OrderController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
@Import({WebConfig.class, QueueInterceptor.class}) // @WebMvc는 Controller는 스캔, 인터셉트나 Config는 넣어줘야 함/
class OrderControllerTest {

    // MockMvc -> WAS(Tomcat)을 띄우지 않고도, 스프링 MVC(HTTP Request/Response)을 재현하는 테스트 도구.
    @Autowired MockMvc mockMvc;
    // 직렬화 : 자바 객체(RequestDto) -> JSON으로 -write / 반대 read를 수행.
    @Autowired ObjectMapper objectMapper;

   // @MockBean Vs @Mock
    // @Mock(Mockito) : 스프링 컨텍스트와 상관 없는 순사 자바 가짜 객체.
    // @MockBean(Spring) : 스프링 컨텍스트에 들어있는 '진짜 빈'을 알아내고, 등록.
   // -> 주의: @MockBean을 쓰면 컨텍스트가 오염되어, 다른 테스트에서 컨텍스트를 재사용하지 못하고 새로 띄울 수 있어 속도가 느려질 수 있습니다.
    @MockBean OrderService orderService;
    @MockBean RedissonLockStockFacade redissonLockStockFacade;
    @MockBean com.project.eshop_refact.service.queue.WaitingQueueService waitingQueueService;

    // Filter Chain 통과를 위한 껍데기들. -> 컨트롤러에 관련된 빈들만 로드 하지만, 자동으로 띄우는 애들도 있음.
    @MockBean
    JwtUtil jwtUtil;
    @MockBean
    UserDetailsServiceImpl userDetailsServiceImpl;

    private UserDetailsImpl testUserDetails;


    //@Test 실행되기 '직전'에 매번 실행. -> 테스트 간의 간섭을 막기위해 매번 새 객체 생성.
    @BeforeEach
    void setUp() {
        // Mock User 생성 (ID 필수) - Heap에 생성.
        User user = new User("user@test.com", "pw", "user", UserRoleEnum.USER);

        // Reflection
        // User의 id는 private -> 외부에서 접근 불가 => Reflection APi를 사용해 접근 제어자 검사(Access Check)를 무시하고 강제로 값 주입
        // 단점 : 컴파일 타임에 타입 체크 불가, JIT 컴파일러 최적화 방해, 캡슐화 깸
        ReflectionTestUtils.setField(user, "id", 1L);

        // 인증 객체 생성
        testUserDetails = new UserDetailsImpl(user);

        // [Test Pattern] Stubbing (행동 정의)
        // "WaitingQueueService야, 누가 와서 isAllowed 물어보면 무조건 true라고 대답해!"
        // any(): ArgumentMatcher. 어떤 인자가 들어오든 상관없음.
        given(waitingQueueService.isAllowed(any())).willReturn(true);
    }

    @Test
    @DisplayName("[실패] 대기열 토큰 없이 주문 요청 시 429 Too Many Requests 반환")
    void createOrder_Fail_When_Queue_Token_Invalid() throws Exception {
        // given
        OrderDto.Request request = new OrderDto.Request();
        request.setProductId(100L);
        request.setCount(1);

        // [핵심] Interceptor 동작 검증
        // setUp에서 true로 설정했지만, 이 테스트에서만 false로 덮어씁니다(Overriding).
        // Interceptor가 waitingQueueService.isAllowed()를 호출했을 때 false가 반환되면 -> false 리턴 -> Controller 진입 차단.
        given(waitingQueueService.isAllowed(anyLong())).willReturn(false);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .with(csrf()) // [Security] CSRF 토큰: POST 요청 시 필수. 없으면 403 Forbidden.
                        .with(user(testUserDetails)) // [Security] MockUser: 인증된 사용자 주입 (로그인 과정 생략)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))) // 객체 -> JSON String 변환
                .andExpect(status().isTooManyRequests()) // [HTTP Status] 429: 요청이 너무 많음 (Rate Limiting)
                .andExpect(jsonPath("$.code").value("QUEUE_WAITING")) // JSON 응답 필드 검증
                .andExpect(jsonPath("$.message").exists())
                .andDo(print()); // 요청/응답 로그 출력 (디버깅용)
    }

    @Test
    @DisplayName("주문 생성 API 성공 테스트")
    void createOrder() throws Exception {
        // given
        OrderDto.Request request = new OrderDto.Request();
        request.setProductId(100L);
        request.setCount(2);

        // Service Mocking
        // Controller는 Facade가 어떻게 동작하는지 모릅니다(Black Box).
        // 그냥 "Facade가 500L을 리턴할 것이다"라고 가정하고, Controller가 그걸 잘 반환하는지만 봅니다.
        given(redissonLockStockFacade.order(eq(1L), eq(100L), eq(2))).willReturn(500L);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .with(csrf()) // CSRF 토큰 필요 -> 없으면 시큐리티에서 403 Forbidden
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

        // ReflectionTestUtils가 반복됩니다.
        // 실무였다면: Order order = Order.createForTest(10L, OrderStatus.ORDER); 같은 Factory Method를 만들었을 겁니다.
        ReflectionTestUtils.setField(order, "id", 10L);
        ReflectionTestUtils.setField(order, "status", OrderStatus.ORDER);

        // [JPA Deep Dive] N+1 문제 방지
        // orderItems를 초기화하지 않고 조회하면, 나중에 접근할 때 NullPointer가 나거나 불필요한 프록시 초기화가 일어날 수 있습니다.
        // 여기서는 빈 리스트를 넣어주어 "연관된 아이템 없음"을 명시합니다.
        ReflectionTestUtils.setField(order, "orderItems", new ArrayList<>()); // N+1 방지용 빈 리스트

        // PageImpl: Spring Data JPA의 Page 인터페이스 구현체
        Page<OrderDto.Response> pageResponse = new PageImpl<>(List.of(new OrderDto.Response(order)));
        // Controller -> Service 호출 시, Page 객체를 리턴하도록 Stubbing
        given(orderService.getOrders(eq(1L), any())).willReturn(pageResponse);

        // when & then
        mockMvc.perform(get("/api/orders")
                        .with(user(testUserDetails)))// GET 요청은 CSRF 토큰 불필요 (조회니까)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(10L))
                .andExpect(jsonPath("$.content[0].orderStatus").value("ORDER"))
                .andDo(print());
    }
}