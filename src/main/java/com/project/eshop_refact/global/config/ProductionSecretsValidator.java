package com.project.eshop_refact.global.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 운영 프로필이 필수 자격 증명 없이, 또는 .env.example의 placeholder 값 그대로 시작되는 것을 방지합니다.
 */
@Component
@Profile("prod")
public class ProductionSecretsValidator {

    // .env.example에 적힌 placeholder 값과 동일하면 실제 값으로 교체되지 않은 것으로 간주합니다.
    private static final Map<String, String> REQUIRED_SECRETS = Map.of(
            "DB_PASSWORD", "your_db_password",
            "JWT_SECRET_KEY", "replace_with_generated_base64_value"
    );

    public ProductionSecretsValidator(Environment environment) {
        var invalidSecrets = REQUIRED_SECRETS.entrySet().stream()
                .filter(entry -> isMissingOrPlaceholder(environment.getProperty(entry.getKey()), entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        if (!invalidSecrets.isEmpty()) {
            throw new IllegalStateException(
                    "Missing or placeholder production environment variables: " + String.join(", ", invalidSecrets)
            );
        }
    }

    private boolean isMissingOrPlaceholder(String value, String placeholder) {
        return !StringUtils.hasText(value) || value.equals(placeholder);
    }
}
