package com.project.eshop_refact.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final LoginLockProperties loginLockProperties;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apply(Long userId, boolean passwordMatches) {
        return userRepository.findByIdForLogin(userId)
                .map(user -> user.applyLoginAttempt(
                        passwordMatches,
                        clock.instant(),
                        loginLockProperties.getMaxFailures(),
                        loginLockProperties.getLockDuration()
                ))
                .orElse(false);
    }
}
