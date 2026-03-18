package com.project.eshop_refact.controller;

import com.project.eshop_refact.dto.ApiResponse;
import com.project.eshop_refact.dto.UserDto;
import com.project.eshop_refact.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}

