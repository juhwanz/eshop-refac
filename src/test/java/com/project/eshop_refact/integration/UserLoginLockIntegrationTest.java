package com.project.eshop_refact.integration;

import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserDto;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.domain.user.UserService;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(UserLoginLockIntegrationTest.MutableClockConfiguration.class)
class UserLoginLockIntegrationTest extends MariaDbRedisIntegrationTest {

    private static final String EMAIL = "login-lock@test.com";
    private static final String PASSWORD = "correct-password";

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MutableClock clock;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        clock.set(Instant.parse("2026-09-08T00:00:00Z"));
    }

    @Test
    @DisplayName("실패 기록은 예외 뒤에도 유지되고 잠금 만료 후 성공 시 초기화된다")
    void failedAttemptsPersistAndTemporaryLockExpires() {
        User user = saveUser();

        for (int attempt = 1; attempt < 5; attempt++) {
            assertLoginFailed("wrong-password");
            User persisted = userRepository.findById(user.getId()).orElseThrow();
            assertThat(persisted.getLoginFailCount()).isEqualTo(attempt);
            assertThat(persisted.getLockedUntil()).isNull();
        }

        assertLoginFailed("wrong-password");
        User locked = userRepository.findById(user.getId()).orElseThrow();
        assertThat(locked.getLoginFailCount()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));

        assertLoginFailed(PASSWORD);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getLockedUntil())
                .isEqualTo(locked.getLockedUntil());

        clock.advance(Duration.ofMinutes(15));
        UserDto.TokenResponse response = userService.login(loginRequest(PASSWORD));

        assertThat(response.getAccessToken()).isNotBlank();
        User reset = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reset.getLoginFailCount()).isZero();
        assertThat(reset.getLockedUntil()).isNull();
    }

    @Test
    @DisplayName("동시 로그인 실패는 갱신 유실 없이 임계치까지 직렬화된다")
    void concurrentFailuresDoNotLoseUpdates() throws InterruptedException {
        User user = saveUser();
        int requestCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(requestCount);
        Queue<Throwable> unexpectedFailures = new ConcurrentLinkedQueue<>();

        try {
            for (int request = 0; request < requestCount; request++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        userService.login(loginRequest("wrong-password"));
                        unexpectedFailures.add(new AssertionError("로그인 실패가 예외 없이 완료되었습니다."));
                    } catch (BusinessException exception) {
                        if (exception.getErrorCode() != ErrorCode.LOGIN_FAILED) {
                            unexpectedFailures.add(exception);
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        unexpectedFailures.add(exception);
                    } catch (Throwable throwable) {
                        unexpectedFailures.add(throwable);
                    } finally {
                        completed.countDown();
                    }
                });
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(completed.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(unexpectedFailures).isEmpty();
        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(persisted.getLoginFailCount()).isEqualTo(5);
        assertThat(persisted.getLockedUntil()).isEqualTo(clock.instant().plus(Duration.ofMinutes(15)));
    }

    private User saveUser() {
        return userRepository.save(new User(
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                "tester",
                UserRoleEnum.USER
        ));
    }

    private void assertLoginFailed(String password) {
        assertThatThrownBy(() -> userService.login(loginRequest(password)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.LOGIN_FAILED);
    }

    private UserDto.LoginRequest loginRequest(String password) {
        UserDto.LoginRequest request = new UserDto.LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(password);
        return request;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-09-08T00:00:00Z"));
        }
    }

    static class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        MutableClock(Instant initialInstant) {
            this.instant = new AtomicReference<>(initialInstant);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        void set(Instant newInstant) {
            instant.set(newInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
