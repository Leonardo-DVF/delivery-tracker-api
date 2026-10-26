package io.github.leonardodvf.deliverytracker.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI deliveryTrackerOpenApi() {
        var validationError = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema());
        var problem = new ObjectSchema()
                .addProperty("type", new StringSchema().format("uri-reference"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri-reference"))
                .addProperty("errors", new ArraySchema().items(validationError));
        var components = new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Paste the accessToken returned by login, without the Bearer prefix."))
                .addSchemas("ApiProblem", problem)
                .addResponses("BadRequest", problemResponse("Invalid input or unknown request fields",
                        Map.of("type", "about:blank", "title", "Bad Request", "status", 400,
                                "detail", "Request validation failed",
                                "errors", List.of(Map.of("field", "email", "message", "must be a well-formed email address")))))
                .addResponses("Unauthorized", problemResponse("Invalid credentials or missing, invalid or expired token",
                        Map.of("type", "about:blank", "title", "Unauthorized", "status", 401,
                                "detail", "Invalid email or password")))
                .addResponses("Conflict", problemResponse("Email already registered or conflicting record",
                        Map.of("type", "about:blank", "title", "Conflict", "status", 409,
                                "detail", "Email already registered")));
        return new OpenAPI()
                .info(new Info().title("Delivery Tracker API").version("v1")
                        .description("Shipment management and tracking API. Public registration always creates a CUSTOMER."))
                .components(components);
    }

    private ApiResponse problemResponse(String description, Map<String, Object> example) {
        return new ApiResponse().description(description).content(new Content()
                .addMediaType(MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiProblem")).example(example)));
    }
}
