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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService userService;
    @MockBean
    JwtUtil jwtUtil;
    @MockBean
    UserDetailsServiceImpl userDetailsServiceImpl;

    @MockBean
    WaitingQueueService waitingQueueService;
    @MockBean
    RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("회원가입 성공: 201 상태코드 반환")
    @WithMockUser // CSRF 토큰 생성을 위해 필요
    void signup_success() throws Exception {
        // given
        UserDto.SignupRequest request = new UserDto.SignupRequest();
        request.setEmail("test@email.com");
        request.setPassword("password123!");
        request.setUsername("tester");

        // when & then
        mockMvc.perform(post("/api/users/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("회원가입 성공"))
                .andDo(print());
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
        request.setEmail("invalid-email"); // 이메일 형식이 아님!
        request.setPassword(""); // 비밀번호 공백!
        request.setUsername("");

        // when & then
        mockMvc.perform(post("/api/users/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()) // 400 에러 기대
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

        //void 메서드는 willThrow()를 먼저 쓰고, given(객체).메서드() 순서로 작성합니다.
        willThrow(new BusinessException(ErrorCode.EMAIL_DUPLICATION))
                .given(userService).signup(any());

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

        // 서비스의 로그인 로직이 비밀번호 불일치 예외를 던지도록 조작
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