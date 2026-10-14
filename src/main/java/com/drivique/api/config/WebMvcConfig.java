package com.drivique.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        java.nio.file.Path path = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location1 = "file:" + path.toString().replace('\\', '/') + "/";
        String location2 = path.toUri().toString();
        if (!location2.endsWith("/")) {
            location2 += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location1, location2);
    }
}
