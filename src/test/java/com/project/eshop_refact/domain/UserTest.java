package com.project.eshop_refact.domain;

import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * User 도메인 단위 테스트
 * 회원(User) 엔티티 생성 시 요구되는 필수 값 검증(Validation) 및 객체 무결성 유지 규칙을
 * 프레임워크 의존성 없이 독립적으로 테스트합니다.
 */
class UserTest {

    @Test
    @DisplayName("성공 : 유효한 데이터로 User 객체가 정상 생성된다")
    void createUser_success() {
        // Given
        String email = "test@example.com";
        String password = "password123!";
        String username = "tester";
        UserRoleEnum role = UserRoleEnum.USER;

        // When
        User user = new User(email, password, username, role);

        // Then
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getPassword()).isEqualTo(password);
        assertThat(user.getUsername()).isEqualTo(username);
        assertThat(user.getRole()).isEqualTo(role);
    }

    @Test
    @DisplayName("실패 : 이메일이 null이거나 빈 값이면 예외가 발생한다")
    void createUser_fail_emptyEmail() {
        // Given
        String invalidEmail = "";

        // When & Then
        assertThatThrownBy(() -> new User(invalidEmail, "password", "tester", UserRoleEnum.USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일은 필수입니다.");
    }

    @Test
    @DisplayName("실패 : 역할(Role)이 누락되면 예외가 발생한다")
    void createUser_fail_nullRole() {
        // Given & When & Then
        assertThatThrownBy(() -> new User("test@test.com", "password", "tester", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("권한 역할은 필수입니다.");
    }
}