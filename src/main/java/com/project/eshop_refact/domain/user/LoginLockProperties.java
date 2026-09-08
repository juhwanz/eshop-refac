package com.project.eshop_refact.domain.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class LoginLockProperties {

    private final int maxFailures;
    private final Duration lockDuration;

    public LoginLockProperties(
            @Value("${app.security.login-lock.max-failures:5}") int maxFailures,
            @Value("${app.security.login-lock.lock-duration:15m}") Duration lockDuration
    ) {
        if (maxFailures <= 0) {
            throw new IllegalArgumentException("로그인 잠금 임계치는 1 이상이어야 합니다.");
        }
        if (lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("로그인 잠금 기간은 0보다 커야 합니다.");
        }
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }
}
