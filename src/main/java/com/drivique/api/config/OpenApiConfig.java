package com.drivique.api.config;

import com.drivique.api.common.exception.ApiProblem;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI driviqueOpenApi(@Value("${info.app.version}") String version) {
        Components components = new Components();
        ModelConverters.getInstance().readAll(ApiProblem.class).forEach(components::addSchemas);
        for (int code : new int[] {400, 401, 403, 404, 409, 500}) {
            components.addResponses("Error" + code, new ApiResponse()
                    .description(org.springframework.http.HttpStatus.valueOf(code).getReasonPhrase())
                    .content(new Content().addMediaType("application/problem+json", new MediaType()
                            .schema(new Schema<>().$ref("#/components/schemas/ApiProblem")))));
        }
        return new OpenAPI().info(new Info().title("Drivique API").version(version)
                .description("API REST compartida por web y móvil. Errores en formato Problem Details. "
                        + "Los endpoints de negocio se incorporan en sus respectivas historias de usuario."))
                .components(components);
    }
}
