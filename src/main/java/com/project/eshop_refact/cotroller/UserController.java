package com.project.eshop_refact.cotroller;

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

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody @Valid UserDto.SignupRequest requestDto){
        userService.signup(requestDto);
        return ResponseEntity.status(201).body("회원가입 성공");
    }

    @PostMapping("/login")
    /*
        로그인 API
        <p>
        Access Token을 Response Body로 반환합니다.
        이유:
         1. 클라이언트(Frontend)에서의 파싱 용이성 및 직관적인 리소스 처리
         2. 추후 token_type(Bearer), expires_in 등 메타데이터 확장에 유리한 JSON 구조 채택
        </p>
     */
    public ResponseEntity<UserDto.TokenResponse> login(@RequestBody @Valid UserDto.LoginRequest requestDto){
        UserDto.TokenResponse tokenDto = userService.login(requestDto);

        return ResponseEntity.ok(tokenDto);
    }
}

