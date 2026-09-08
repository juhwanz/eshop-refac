package com.project.eshop_refact.config;

import com.project.eshop_refact.domain.queue.QueueController;
import com.project.eshop_refact.domain.queue.QueueDto;
import com.project.eshop_refact.domain.queue.QueueStatus;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.global.security.JwtAuthenticationFilter;
import com.project.eshop_refact.global.security.JwtUtil;
import com.project.eshop_refact.global.security.RestAccessDeniedHandler;
import com.project.eshop_refact.global.security.RestAuthenticationEntryPoint;
import com.project.eshop_refact.global.security.SecurityConfig;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import com.project.eshop_refact.global.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = QueueController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class QueueEndpointSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockBean WaitingQueueService waitingQueueService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean RedisTemplate<String, String> redisTemplate;

    UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        User user = new User("queue-security@test.com", "pw", "queue", UserRoleEnum.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        userDetails = new UserDetailsImpl(user);
    }

    @Test
    void productQueueStatusRequiresAuthenticationEvenThoughProductQueriesArePublic() throws Exception {
        mockMvc.perform(get("/api/products/10/queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReadProductQueueStatus() throws Exception {
        given(waitingQueueService.getStatus(10L, 1L))
                .willReturn(new QueueDto.Response(QueueStatus.NOT_REGISTERED, null));

        mockMvc.perform(get("/api/products/10/queue").with(user(userDetails)))
                .andExpect(status().isOk());
    }
}
