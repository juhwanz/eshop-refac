package com.project.eshop_refact.service;

import com.project.eshop_refact.domain.user.UserService;
import com.project.eshop_refact.global.security.JwtUtil;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.domain.user.UserDto;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserService 비즈니스 로직 단위 테스트
 * 데이터베이스 및 외부 인프라(Redis, Security)와의 결합을 끊고, Mockito를 활용하여
 * 회원가입(중복 검증, 암호화) 및 로그인(토큰 발급)의 핵심 도메인 흐름을 격리하여 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock private RedisTemplate<String, String> redisTemplate;

    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("회원가입 성공 시나리오")
    void signup_success() {
        //given
        UserDto.SignupRequest request= new UserDto.SignupRequest();
        request.setEmail("test@test.com");
        request.setPassword("1234");
        request.setUsername("tester");

        // 이메일 중복 검증 통과 및 비밀번호 암호화 동작을 Stubbing 합니다.
        when(userRepository.findByEmail(request.getEmail()))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.getPassword()))
                .thenReturn("encoded_pw");

        // When
        userService.signup(request);

        // Then
        // 영속성 계층(Repository)의 save 메서드가 정상적으로 1회 호출되었는지 행위를 검증합니다.
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_fail_duplicate() {
        // Given
        UserDto.SignupRequest request = new UserDto.SignupRequest();
        request.setEmail("duplicate@test.com");

        // 저장소에 이미 동일한 이메일이 존재하는 예외 상황을 가정합니다.
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(new User())); // 이미 존재

        // When & Then
        // 비즈니스 예외가 발생하여 회원가입 흐름이 정상적으로 차단되는지 검증합니다.
        assertThrows(BusinessException.class, () -> userService.signup(request));
    }

    @Test
    @DisplayName("로그인 성공")
    void login_success() {
        // Given
        UserDto.LoginRequest request = new UserDto.LoginRequest();
        request.setEmail("test@test.com");
        request.setPassword("1234");

        User fakeUser = new User("test@test.com", "encodedPw", "tester", UserRoleEnum.USER);

        // 정상적인 회원 조회, 비밀번호 일치, 그리고 JWT 토큰 발급 동작을 정의합니다.
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(fakeUser));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(jwtUtil.createToken(any(), any())).thenReturn("access");
        when(jwtUtil.createRefreshToken(any())).thenReturn("refresh");

        // Refresh Token의 Redis 저장을 위한 외부 의존성 동작을 허용합니다.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        UserDto.TokenResponse response = userService.login(request);

        // Then
        // 반환된 응답 DTO에 토큰 정보가 올바르게 매핑되었는지 검증합니다.
        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
    }
}