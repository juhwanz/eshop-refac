package com.project.eshop_refact.domain;

import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

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

    @Test
    @DisplayName("연속 실패가 임계치에 도달하면 임시 잠금 만료 시각을 설정한다")
    void loginFailureLocksTemporarilyAtThreshold() {
        User user = new User("test@test.com", "password", "tester", UserRoleEnum.USER);
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        Duration lockDuration = Duration.ofMinutes(15);

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThat(user.applyLoginAttempt(false, now, 5, lockDuration)).isFalse();
            assertThat(user.getLoginFailCount()).isEqualTo(attempt);
            assertThat(user.getLockedUntil()).isNull();
        }

        assertThat(user.applyLoginAttempt(false, now, 5, lockDuration)).isFalse();
        assertThat(user.getLoginFailCount()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(now.plus(lockDuration));
    }

    @Test
    @DisplayName("활성 잠금 중 추가 실패는 잠금 기간을 연장하지 않는다")
    void activeLockDoesNotExtend() {
        User user = new User("test@test.com", "password", "tester", UserRoleEnum.USER);
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        Duration lockDuration = Duration.ofMinutes(15);
        for (int attempt = 0; attempt < 5; attempt++) {
            user.applyLoginAttempt(false, now, 5, lockDuration);
        }
        Instant lockedUntil = user.getLockedUntil();

        assertThat(user.applyLoginAttempt(false, now.plusSeconds(60), 5, lockDuration)).isFalse();

        assertThat(user.getLoginFailCount()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isEqualTo(lockedUntil);
    }

    @Test
    @DisplayName("잠금 만료 후 성공하면 실패 상태를 초기화한다")
    void successfulLoginAfterExpirationResetsFailureState() {
        User user = new User("test@test.com", "password", "tester", UserRoleEnum.USER);
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        Duration lockDuration = Duration.ofMinutes(15);
        for (int attempt = 0; attempt < 5; attempt++) {
            user.applyLoginAttempt(false, now, 5, lockDuration);
        }

        assertThat(user.applyLoginAttempt(true, now.plus(lockDuration), 5, lockDuration)).isTrue();

        assertThat(user.getLoginFailCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }
}
