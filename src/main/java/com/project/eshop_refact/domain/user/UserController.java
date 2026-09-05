package com.project.eshop_refact.domain.user;

import com.project.eshop_refact.global.common.ApiResponse;
import com.project.eshop_refact.global.common.ErrorResponse;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Tag(name = "사용자", description = "회원가입, 로그인 및 토큰 관리 API")
public class UserController {

    private final UserService userService;

    /**
     * 회원가입 API
     */
    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 사용자 이름으로 일반 사용자 계정을 생성합니다.")
    @SecurityRequirements
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 오류 또는 이미 가입된 이메일",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "동시에 등록된 중복 이메일",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signup(@RequestBody @Valid UserDto.SignupRequest requestDto){
        userService.signup(requestDto);
        return ResponseEntity.status(201).body(ApiResponse.success("회원가입 성공"));
    }

    /**
     * 로그인 API
     * 인증 성공 시 클라이언트에게 엑세스(Access) 및 리프레시(Refresh) 토큰을 발급합니다.
     */
    @Operation(summary = "로그인", description = "이메일과 비밀번호를 검증하고 Access/Refresh Token을 발급합니다.")
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserDto.TokenResponse>> login(@RequestBody @Valid UserDto.LoginRequest requestDto){
        UserDto.TokenResponse tokenDto = userService.login(requestDto);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", tokenDto));
    }

    /**
     * 토큰 재발급 API
     * 리프레시 토큰을 검증하여 만료된 엑세스 토큰을 갱신합니다.
     */
    @Operation(summary = "토큰 재발급", description = "유효한 Refresh Token으로 Access/Refresh Token을 재발급합니다.")
    @SecurityRequirements
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<UserDto.TokenResponse>> reissue(@RequestBody @Valid UserDto.RefreshRequest requestDto) {
        UserDto.TokenResponse tokenDto = userService.reissue(requestDto);
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공", tokenDto));
    }

    /**
     * 로그아웃 API
     * 현재 사용자의 엑세스 토큰을 무효화하고 인증 상태를 해제합니다.
     */
    @Operation(summary = "로그아웃", description = "Refresh Token을 제거하고 현재 Access Token을 블랙리스트에 등록합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestHeader String authorizationHeader, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        String accessToken = authorizationHeader.substring(7);

        userService.logout(accessToken, userDetails.getUser().getEmail());
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공"));
    }
}
