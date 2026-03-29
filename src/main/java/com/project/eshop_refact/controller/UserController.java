package com.project.eshop_refact.controller;

import com.project.eshop_refact.dto.ApiResponse;
import com.project.eshop_refact.dto.UserDto;
import com.project.eshop_refact.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // 회원가입: @Valid를 통한 입력값 검증 및 Fail-Fast 적용
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@RequestBody @Valid UserDto.SignupRequest requestDto){
        userService.signup(requestDto);
        return ResponseEntity.status(201).body(ApiResponse.success("회원가입 성공"));
    }

    // 로그인: 확장성을 고려하여 JSON Body로 토큰 반환 (RESTful 설계)
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserDto.TokenResponse>> login(@RequestBody @Valid UserDto.LoginRequest requestDto){
        UserDto.TokenResponse tokenDto = userService.login(requestDto);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", tokenDto));
    }

    // 토큰 재발급 : AccessToken 만료시 Refresh Token 이용해 재 발급
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<UserDto.TokenResponse>> reissue(@RequestBody @Valid UserDto.RefreshRequest requestDto) {
        UserDto.TokenResponse tokenDto = userService.reissue(requestDto);
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공", tokenDto));
    }

    // 로그아웃: SecurityContext에서 추출한 정보로 Redis 토큰 삭제
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal com.project.eshop_refact.config.UserDetailsImpl userDetails) {
        userService.logout(userDetails.getUser().getEmail());
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공"));
    }
}


