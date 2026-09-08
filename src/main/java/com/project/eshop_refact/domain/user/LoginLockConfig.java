package com.project.eshop_refact.domain.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class LoginLockConfig {

    @Bean
    public Clock loginClock() {
        return Clock.systemUTC();
    }
}
