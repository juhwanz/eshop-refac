package com.project.eshop_refact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

public class UserDto {

    @Getter
    @Setter
    public static class SignupRequest {

        @NotBlank(message = "이메일은 필수 입력")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        private String email;

        @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
        // 최소 8자, 하나 이상의 문자, 하나의 숫자 및 하나의 특수 문자 포함 정규식
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
                message = "비밀번호는 8자 이상, 영문, 숫자, 특수문자를 포함해야 합니다.")
        // Plain Text -> DB 저장 X -> Service에서 암호화 후 저장.
        private String password;

        @NotBlank(message = "사용자 이름은 필수 입력 값입니다.")
        @Size(min = 2, max = 10, message = "이름은 2자 이상 10자 이하로 입력해주세요.")
        private String username;

    }

    @Getter
    @Setter
    public static class LoginRequest {

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        private String email;

        @NotBlank(message = "비밀번호를 입력해주세요.")
        private String password;
    }

    @Getter
    @AllArgsConstructor
    public static class TokenResponse {
        private String accessToken;
        private String refreshToken;
    }
}
