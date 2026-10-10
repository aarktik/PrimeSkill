package com.example.toolhub.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI primeskillOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Primeskill API")
                        .version("v1")
                        .description("REST API for the Primeskill tool registry and marketplace."));
    }
}
