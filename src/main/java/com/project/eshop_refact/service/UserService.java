package com.project.eshop_refact.service;


import com.project.eshop_refact.config.JwtUtil;
import com.project.eshop_refact.domain.User;
import com.project.eshop_refact.domain.UserRoleEnum;
import com.project.eshop_refact.dto.UserDto;
import com.project.eshop_refact.exception.BusinessException;
import com.project.eshop_refact.exception.ErrorCode;
import com.project.eshop_refact.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 조회 성능 최적화 (Dirty Checking 비용 절감)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional  // 쓰기 작업: 데이터 정합성 보장
    public void signup(UserDto.SignupRequest requestDto){
        String email = requestDto.getEmail();

        Optional<User> checkUser = userRepository.findByEmail(email);
        if(checkUser.isPresent()) throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);

        // 보안: 단방향 해싱 알고리즘(BCrypt) 적용
        String password = passwordEncoder.encode(requestDto.getPassword());
        UserRoleEnum role = UserRoleEnum.USER;

        User user = new User(requestDto.getEmail(), password, requestDto.getUsername(), role);
        userRepository.save(user);
    }


    public UserDto.TokenResponse login(UserDto.LoginRequest requestDto){
        User user = userRepository.findByEmail(requestDto.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())){
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtUtil.createToken(user.getEmail(), user.getRole());

        String refreshToken = jwtUtil.createRefreshToken(user.getEmail());

        // Refresh Token 저장소로 Redis 채택 (TTL 설정으로 생명주기 자동 관리)
        redisTemplate.opsForValue().set(
                "RT:" + user.getEmail(), refreshToken, 14, TimeUnit.DAYS
        );

        return new UserDto.TokenResponse(accessToken, refreshToken);
    }
}

