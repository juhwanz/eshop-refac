package com.project.eshop_refact.controller;

import com.project.eshop_refact.domain.queue.QueueController;
import com.project.eshop_refact.domain.queue.QueueDto;
import com.project.eshop_refact.domain.queue.QueueStatus;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.global.security.JwtUtil;
import com.project.eshop_refact.global.security.SecurityConfig;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import com.project.eshop_refact.global.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QueueController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
class QueueControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean WaitingQueueService waitingQueueService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;

    UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        User user = new User("queue@test.com", "pw", "queue", UserRoleEnum.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        userDetails = new UserDetailsImpl(user);
    }

    @Test
    void firstRegistrationReturnsCreated() throws Exception {
        QueueDto.Response response = new QueueDto.Response(QueueStatus.WAITING, 1L);
        given(waitingQueueService.register(10L, 1L))
                .willReturn(new WaitingQueueService.Registration(response, true));

        mockMvc.perform(post("/api/products/10/queue").with(csrf()).with(user(userDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.rank").value(1));
    }

    @Test
    void duplicateRegistrationReturnsCurrentState() throws Exception {
        QueueDto.Response response = new QueueDto.Response(QueueStatus.ACTIVE, null);
        given(waitingQueueService.register(10L, 1L))
                .willReturn(new WaitingQueueService.Registration(response, false));

        mockMvc.perform(post("/api/products/10/queue").with(csrf()).with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.rank").doesNotExist());
    }

    @Test
    void statusReturnsNotRegisteredWithoutTreatingItAsAnError() throws Exception {
        given(waitingQueueService.getStatus(10L, 1L))
                .willReturn(new QueueDto.Response(QueueStatus.NOT_REGISTERED, null));

        mockMvc.perform(get("/api/products/10/queue").with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_REGISTERED"));
    }

    @Test
    void anonymousRegistrationIsRejected() throws Exception {
        mockMvc.perform(post("/api/products/10/queue").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
