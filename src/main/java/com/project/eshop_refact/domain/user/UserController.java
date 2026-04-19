package com.project.eshop_refact.domain.user;

import com.project.eshop_refact.global.common.ApiResponse;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    /**
     * 회원가입 API
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@RequestBody @Valid UserDto.SignupRequest requestDto){
        userService.signup(requestDto);
        return ResponseEntity.status(201).body(ApiResponse.success("회원가입 성공"));
    }

    /**
     * 로그인 API
     * 인증 성공 시 클라이언트에게 엑세스(Access) 및 리프레시(Refresh) 토큰을 발급합니다.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserDto.TokenResponse>> login(@RequestBody @Valid UserDto.LoginRequest requestDto){
        UserDto.TokenResponse tokenDto = userService.login(requestDto);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", tokenDto));
    }

    /**
     * 토큰 재발급 API
     * 리프레시 토큰을 검증하여 만료된 엑세스 토큰을 갱신합니다.
     */
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<UserDto.TokenResponse>> reissue(@RequestBody @Valid UserDto.RefreshRequest requestDto) {
        UserDto.TokenResponse tokenDto = userService.reissue(requestDto);
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공", tokenDto));
    }

    /**
     * 로그아웃 API
     * 현재 사용자의 엑세스 토큰을 무효화하고 인증 상태를 해제합니다.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader String authorizationHeader, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        String accessToken = authorizationHeader.substring(7);

        userService.logout(accessToken, userDetails.getUser().getEmail());
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공"));
    }
}


