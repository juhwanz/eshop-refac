package com.project.eshop_refact.config;

import com.project.eshop_refact.global.config.ProductionSecretsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSecretsValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ProductionSecretsValidator.class);

    @Test
    void prodProfileFailsToStartWhenRequiredSecretsAreMissing() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause()
                            .hasMessage("Missing required production environment variables: DB_PASSWORD, JWT_SECRET_KEY");
                });
    }

    @Test
    void prodProfileStartsWhenRequiredSecretsAreProvided() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "DB_PASSWORD=test-db-password",
                        "JWT_SECRET_KEY=test-jwt-secret"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductionSecretsValidator.class);
                });
    }

    @Test
    void nonProdProfileDoesNotRequireProductionSecrets() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ProductionSecretsValidator.class);
                });
    }
}
