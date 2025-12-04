package com.remus.dwanjcex.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini-CEX API")
                        .version("1.0.0")
                        .description("Mini centralized exchange demo API")
                        .contact(new Contact().name("Remus").email("remus@example.com")))
                .externalDocs(new ExternalDocumentation()
                        .description("Project GitHub")
                        .url("https://github.com/your-repo"));
    }
}