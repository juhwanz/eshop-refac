package com.project.eshop_refact.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionSecurityConfigurationTest {

    @Test
    void applicationConfigurationRequiresSecretsAndOnlyExposesHealthWithoutDetails() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("src/main/resources/application.yaml"));
        Properties properties = yaml.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(properties.getProperty("jwt.secret")).isEqualTo("${JWT_SECRET_KEY}");
        assertThat(properties.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(properties.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    void productionComposeRejectsMissingSecretsWithoutFallbacks() throws IOException {
        String compose = Files.readString(Path.of("docker-compose.prod.yml"));

        assertThat(compose)
                .contains("${DB_PASSWORD:?DB_PASSWORD is required}")
                .contains("${JWT_SECRET_KEY:?JWT_SECRET_KEY is required}")
                .doesNotContain("${DB_PASSWORD:-")
                .doesNotContain("${JWT_SECRET_KEY:-");
    }
}
