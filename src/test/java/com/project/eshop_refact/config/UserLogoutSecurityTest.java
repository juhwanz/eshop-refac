package com.project.eshop_refact.config;

import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.domain.user.UserController;
import com.project.eshop_refact.domain.user.UserService;
import com.project.eshop_refact.global.security.JwtAuthenticationFilter;
import com.project.eshop_refact.global.security.JwtUtil;
import com.project.eshop_refact.global.security.RestAccessDeniedHandler;
import com.project.eshop_refact.global.security.RestAuthenticationEntryPoint;
import com.project.eshop_refact.global.security.SecurityConfig;
import com.project.eshop_refact.global.security.UserDetailsServiceImpl;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class UserLogoutSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserService userService;

    @MockBean
    JwtUtil jwtUtil;

    @MockBean
    UserDetailsServiceImpl userDetailsServiceImpl;

    @MockBean
    RedisTemplate<String, String> redisTemplate;

    @MockBean
    WaitingQueueService waitingQueueService;

    @Test
    @DisplayName("로그아웃 보안 경계: Authorization 헤더 누락을 401로 거절한다")
    void rejects_missing_authorization_header() throws Exception {
        assertUnauthorized(post("/api/users/logout"));
    }

    @Test
    @DisplayName("로그아웃 보안 경계: 잘못된 인증 scheme을 401로 거절한다")
    void rejects_invalid_authorization_scheme() throws Exception {
        assertUnauthorized(post("/api/users/logout")
                .header(HttpHeaders.AUTHORIZATION, "Basic access-token"));
    }

    @Test
    @DisplayName("로그아웃 보안 경계: 빈 Bearer Token을 401로 거절한다")
    void rejects_empty_bearer_token() throws Exception {
        given(jwtUtil.getClaimsIfValid("")).willReturn(null);

        assertUnauthorized(post("/api/users/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer "));
    }

    @Test
    @DisplayName("로그아웃 보안 경계: Refresh Token을 401로 거절한다")
    void rejects_refresh_token() throws Exception {
        Claims claims = mock(Claims.class);
        given(jwtUtil.getClaimsIfValid("refresh-token")).willReturn(claims);
        given(claims.get("type")).willReturn("REFRESH");

        mockMvc.perform(post("/api/users/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(content().string(not(containsString("refresh-token"))));

        verifyNoInteractions(userService);
    }

    private void assertUnauthorized(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(userService);
    }
}
