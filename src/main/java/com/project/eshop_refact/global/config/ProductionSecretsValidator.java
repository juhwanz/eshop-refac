package com.project.eshop_refact.global.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 운영 프로필이 필수 자격 증명 없이 시작되는 것을 방지합니다.
 */
@Component
@Profile("prod")
public class ProductionSecretsValidator {

    private static final List<String> REQUIRED_SECRET_NAMES = List.of("DB_PASSWORD", "JWT_SECRET_KEY");

    public ProductionSecretsValidator(Environment environment) {
        List<String> missingSecrets = REQUIRED_SECRET_NAMES.stream()
                .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
                .toList();

        if (!missingSecrets.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required production environment variables: " + String.join(", ", missingSecrets)
            );
        }
    }
}
