package com.project.eshop_refact.config;

import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.global.security.JwtUtil;
import com.project.eshop_refact.global.security.RestAccessDeniedHandler;
import com.project.eshop_refact.global.security.RestAuthenticationEntryPoint;
import com.project.eshop_refact.global.security.SecurityConfig;
import com.project.eshop_refact.global.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActuatorSecurityTest.TestActuatorController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        ActuatorSecurityTest.TestActuatorController.class
})
class ActuatorSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtUtil jwtUtil;

    @MockBean
    UserDetailsServiceImpl userDetailsServiceImpl;

    @MockBean
    RedisTemplate<String, String> redisTemplate;

    @MockBean
    WaitingQueueService waitingQueueService;

    @Test
    void healthIsPublicWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void otherActuatorEndpointsRejectAuthenticatedExternalRequests() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class TestActuatorController {

        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }

        @GetMapping("/actuator/metrics")
        Map<String, String> metrics() {
            return Map.of("metric", "sensitive-value");
        }
    }
}
