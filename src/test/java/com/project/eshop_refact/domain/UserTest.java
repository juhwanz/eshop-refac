package com.project.eshop_refact.domain;

import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        // Given & When & Then (불필요한 변수 제거 및 직접 null 주입)
        assertThatThrownBy(() -> new User("test@test.com", "password", "tester", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("권한 역할은 필수입니다.");
    }
}