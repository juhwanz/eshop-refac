package com.project.eshop_refact.controller;

import com.project.eshop_refact.domain.user.UserController;
import com.project.eshop_refact.global.security.JwtUtil;
import com.project.eshop_refact.global.security.SecurityConfig;
import com.project.eshop_refact.domain.user.UserDto;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.global.security.UserDetailsServiceImpl;
import com.project.eshop_refact.domain.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController 웹 계층 슬라이스 테스트
 * SecurityConfig를 배제하고 컨트롤러의 요청 매핑, 입력값 검증(@Valid), 예외 처리 로직을 격리하여 테스트합니다.
 */
@WebMvcTest(controllers = UserController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService userService;

    // WebMvcTest 환경에서 Security Filter Chain 및 기타 Interceptor 통과를 위한 Mocking
    @MockBean
    JwtUtil jwtUtil;
    @MockBean
    UserDetailsServiceImpl userDetailsServiceImpl;

    @MockBean
    WaitingQueueService waitingQueueService;
    @MockBean
    RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("루트 경로 접근: Swagger UI로 리다이렉트")
    @WithMockUser
    void root_redirects_to_swagger_ui() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui.html"));
    }

    @Test
    @DisplayName("회원가입 성공: 201 상태코드 반환")
    @WithMockUser // 컨트롤러 접근 권한 검증을 통과하기 위한 Mock 인증 객체 주입
    void signup_success() throws Exception {
        // given
        UserDto.SignupRequest request = new UserDto.SignupRequest();
        request.setEmail("test@email.com");
        request.setPassword("password123!");
        request.setUsername("tester");

        // when & then
        mockMvc.perform(post("/api/users/signup")
                        .with(csrf()) // 상태 변경(POST) 요청 시 Security 정책 충족을 위한 CSRF 토큰 주입
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("회원가입 성공"))
                .andDo(print());
    }

    @Test
    @DisplayName("회원가입 실패: GET 요청은 405와 허용 메서드를 반환")
    @WithMockUser
    void signup_fail_method_not_allowed() throws Exception {
        mockMvc.perform(get("/api/users/signup"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("지원하지 않는 HTTP 메서드입니다."));
    }

    @Test
    @DisplayName("로그인 성공: 헤더에 토큰이 포함되어야 한다")
    @WithMockUser
    void login_success() throws Exception {
        // given
        UserDto.LoginRequest request = new UserDto.LoginRequest();
        request.setEmail("test@email.com");
        request.setPassword("password");

        UserDto.TokenResponse tokenResponse = new UserDto.TokenResponse("access-token", "refresh-token");
        given(userService.login(any())).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/api/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("로그인 성공"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andDo(print());
    }

    @Test
    @DisplayName("회원가입 실패: 유효하지 않은 입력값 (400 Bad Request)")
    @WithMockUser
    void signup_fail_invalid_input() throws Exception {
        // given
        UserDto.SignupRequest request = new UserDto.SignupRequest();
        request.setEmail("invalid-email");
        request.setPassword("");
        request.setUsername("");

        // when & then
        mockMvc.perform(post("/api/users/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("로그인 실패 : 유효하지 않은 입력값 (400 Bad Request)")
    @WithMockUser
    void login_fail_invalid_input() throws Exception{
        //given
        UserDto.LoginRequest request = new UserDto.LoginRequest();
        request.setEmail("no-email-format");
        request.setPassword("");

        //when & then
        mockMvc.perform(post("/api/users/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("회원가입 실패: 이미 존재하는 이메일 (비즈니스 예외)")
    @WithMockUser
    void signup_fail_duplicated_email() throws Exception{
        // given
        UserDto.SignupRequest request = new UserDto.SignupRequest();
        request.setEmail("duplicate@email.com");
        request.setPassword("password123!");
        request.setUsername("tester");

        willThrow(new BusinessException(ErrorCode.EMAIL_DUPLICATION))
                .given(userService).signup(any());

        // When & Then
        mockMvc.perform(post("/api/users/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    @Test
    @DisplayName("로그인 실패 : 비밀번호 불일치")
    @WithMockUser
    void login_fail_password() throws Exception{
        //given
        UserDto.LoginRequest request = new UserDto.LoginRequest();
        request.setEmail("test@email.com");
        request.setPassword("wrongPassword!!!");

        given(userService.login(any())).willThrow(new BusinessException(ErrorCode.LOGIN_FAILED));

        // when & then
        mockMvc.perform(post("/api/users/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // 또는 401(Unauthorized)
                .andDo(print());
    }
}
