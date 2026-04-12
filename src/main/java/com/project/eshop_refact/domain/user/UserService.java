package com.project.eshop_refact.domain.user;


import com.project.eshop_refact.global.security.JwtUtil;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
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

        //계정 상태 검증 로직
        if(user.getStatus() == UserStatus.LOCKED){
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if(user.getStatus() == UserStatus.DELETED){
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if(!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())){
            user.handleLoginFailure();
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        user.resetLoginFailCount();

        // 토큰 발급 로직
        String accessToken = jwtUtil.createToken(user.getEmail(), user.getRole());

        String refreshToken = jwtUtil.createRefreshToken(user.getEmail());

        // Refresh Token 저장소로 Redis 채택 (TTL 설정으로 생명주기 자동 관리)
        redisTemplate.opsForValue().set(
                "RT:" + user.getEmail(), refreshToken, 14, TimeUnit.DAYS
        );

        return new UserDto.TokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public UserDto.TokenResponse reissue(UserDto.RefreshRequest requestDto){
        String refreshToken = requestDto.getRefreshToken();

        // 검증
        Claims claims = jwtUtil.getClaimsIfValid(refreshToken);
        if(claims == null || !claims.get("type").equals("REFRESH") ){
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String email = claims.getSubject();
        String redisKey = "RT:" + email;

        // Redis 토큰과 비교(탈취 방지)
        String savedToken = redisTemplate.opsForValue().get(redisKey);
        if(savedToken == null || !savedToken.equals(refreshToken)){
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 토큰 재랍급
        String newAccessToken = jwtUtil.createToken(user.getEmail(), user.getRole());
        String newRefreshToken = jwtUtil.createRefreshToken(user.getEmail());

        redisTemplate.opsForValue().set(redisKey, newRefreshToken, 14, TimeUnit.DAYS);

        return new UserDto.TokenResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String email){
        String redisKey = "RT:" + email;
        if(Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))){
            redisTemplate.delete(redisKey);
        }
    }
}