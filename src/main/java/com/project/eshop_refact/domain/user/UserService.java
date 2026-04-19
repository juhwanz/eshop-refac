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

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
// 불필요한 Dirty Checking을 방지하여 전반적인 조회 성능을 최적화합니다.
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional  // 쓰기 작업
    public void signup(UserDto.SignupRequest requestDto){
        String email = requestDto.getEmail();

        Optional<User> checkUser = userRepository.findByEmail(email);
        if(checkUser.isPresent()) throw new BusinessException(ErrorCode.EMAIL_DUPLICATION);

        // 비밀번호는 단방향 해시 함수를 통해 암호화하여 저장합니다.
        String password = passwordEncoder.encode(requestDto.getPassword());
        UserRoleEnum role = UserRoleEnum.USER;

        User user = new User(requestDto.getEmail(), password, requestDto.getUsername(), role);
        userRepository.save(user);
    }

    @Transactional
    public UserDto.TokenResponse login(UserDto.LoginRequest requestDto){
        User user = userRepository.findByEmail(requestDto.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(user.getStatus() == UserStatus.LOCKED){
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if(user.getStatus() == UserStatus.DELETED){
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if(!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())){
            user.handleLoginFailure();  // 실패 횟수 증가
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        user.resetLoginFailCount();

        String accessToken = jwtUtil.createToken(user.getEmail(), user.getRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getEmail());

        // Refresh Token은 Redis에 저장하여 TTL 기반으로 생명주기를 안전하게 관리합니다
        redisTemplate.opsForValue().set(
                "RT:" + user.getEmail(), refreshToken, 14, TimeUnit.DAYS
        );

        return new UserDto.TokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public UserDto.TokenResponse reissue(UserDto.RefreshRequest requestDto){
        String refreshToken = requestDto.getRefreshToken();

        Claims claims = jwtUtil.getClaimsIfValid(refreshToken);
        if(claims == null || !claims.get("type").equals("REFRESH") ){
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        String email = claims.getSubject();
        String redisKey = "RT:" + email;

        // Redis에 저장된 원본 토큰과 비교하여 토큰 탈취 및 변조를 방지합니다.
        String savedToken = redisTemplate.opsForValue().get(redisKey);
        if(savedToken == null || !savedToken.equals(refreshToken)){
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtUtil.createToken(user.getEmail(), user.getRole());
        String newRefreshToken = jwtUtil.createRefreshToken(user.getEmail());

        redisTemplate.opsForValue().set(redisKey, newRefreshToken, 14, TimeUnit.DAYS);

        return new UserDto.TokenResponse(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logout(String accessToken, String email){
        String redisKey = "RT:" + email;

        redisTemplate.delete(redisKey);

        long expiration = jwtUtil.getExpiration(accessToken);
        long now = System.currentTimeMillis();
        long ttl = expiration - now;

        if(ttl > 0){
            redisTemplate.opsForValue().set(
                    "BLACKLIST:" + accessToken, "logout", ttl, TimeUnit.MILLISECONDS
            );
        }
    }
}