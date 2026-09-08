package com.project.eshop_refact.service;

import com.project.eshop_refact.domain.user.LoginAttemptService;
import com.project.eshop_refact.domain.user.LoginLockProperties;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginAttemptServiceTest {

    @Test
    @DisplayName("행 잠금으로 조회한 사용자에 로그인 실패 상태를 반영한다")
    void appliesFailureToLockedUserRow() {
        UserRepository userRepository = mock(UserRepository.class);
        User user = new User("test@test.com", "password", "tester", UserRoleEnum.USER);
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        LoginLockProperties properties = new LoginLockProperties(5, Duration.ofMinutes(15));
        LoginAttemptService service = new LoginAttemptService(
                userRepository,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(userRepository.findByIdForLogin(1L)).thenReturn(Optional.of(user));

        assertThat(service.apply(1L, false)).isFalse();

        assertThat(user.getLoginFailCount()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }
}
