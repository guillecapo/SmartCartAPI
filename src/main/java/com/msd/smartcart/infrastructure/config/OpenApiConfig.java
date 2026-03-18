package com.msd.smartcart.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartCartOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartCart API")
                        .description("""
                                Production-ready shopping cart API built with Hexagonal Architecture and DDD principles.
                                
                                ## Authentication
                                All endpoints except `/auth/**` require a Bearer JWT token.
                                Register or login to obtain a token, then use it in the Authorize button above.
                                
                                ## Idempotency
                                `POST /v1/carts/products` and `POST /v1/checkout` require an `Idempotency-Key` header.
                                Use a unique UUID per operation — retries with the same key return the cached response.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SmartCart API")
                                .url("https://github.com/tu-usuario/smartcart-api")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer JWT"))
                .components(new Components()
                        .addSecuritySchemes("Bearer JWT", new SecurityScheme()
                                .name("Bearer JWT")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Ingresa el token JWT obtenido en /auth/login o /auth/register")
                        ));
    }
}