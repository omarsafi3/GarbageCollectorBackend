package com.municipality.garbagecollectorbackend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI garbageCollectorOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
                .info(new Info()
                        .title("Garbage Collector Backend API")
                        .description("""
                                RESTful API for the Municipality Garbage Collection Management System.
                                
                                This API provides endpoints for:
                                - **Authentication**: Login and token management
                                - **Bins**: Manage garbage bins, fill levels, and locations
                                - **Vehicles**: Fleet management and dispatch
                                - **Routes**: Route optimization and assignment
                                - **Employees**: Staff management (drivers, collectors)
                                - **Departments**: Department management
                                - **Incidents**: Report and manage collection incidents
                                
                                **Real-time Updates**: The system also supports WebSocket connections for real-time bin updates and vehicle tracking.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Municipality IT Team")
                                .email("support@municipality.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token. The token is obtained from the /auth/login endpoint.")));
    }
}
