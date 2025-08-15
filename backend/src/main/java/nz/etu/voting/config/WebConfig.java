package nz.etu.voting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;
import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:3000",
                        "https://events.etu.nz",
                        "http://10.0.9.238:3000"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {

                        Resource resource = location.createRelative(resourcePath);
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }

                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("public/")) {
                            return null;
                        }

                        if (resourcePath.contains(".")) {
                            return null;
                        }

                        String indexPath = resourcePath + "/index.html";
                        Resource indexResource = location.createRelative(indexPath);
                        if (indexResource.exists() && indexResource.isReadable()) {
                            return indexResource;
                        }

                        if (!resourcePath.endsWith("/")) {
                            indexPath = resourcePath + "/index.html";
                            indexResource = location.createRelative(indexPath);
                            if (indexResource.exists() && indexResource.isReadable()) {
                                return indexResource;
                            }
                        }

                        logger.debug("Resource not found, falling back to root index.html");
                        return location.createRelative("index.html");
                    }
                });
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Root routes
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/admin").setViewName("forward:/admin/index.html");
        registry.addViewController("/register").setViewName("forward:/register/index.html");
        registry.addViewController("/success").setViewName("forward:/success/index.html");
        registry.addViewController("/ticket").setViewName("forward:/ticket/index.html");
        registry.addViewController("/bmm").setViewName("forward:/bmm/index.html");

        // With trailing slash
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
        registry.addViewController("/register/").setViewName("forward:/register/index.html");
        registry.addViewController("/success/").setViewName("forward:/success/index.html");
        registry.addViewController("/ticket/").setViewName("forward:/ticket/index.html");
        registry.addViewController("/bmm/").setViewName("forward:/bmm/index.html");

        // Add sub-routes for register
        registry.addViewController("/register/form").setViewName("forward:/register/form/index.html");
        registry.addViewController("/register/form/").setViewName("forward:/register/form/index.html");
        registry.addViewController("/register/bmm-template").setViewName("forward:/register/bmm-template/index.html");
        registry.addViewController("/register/special-vote").setViewName("forward:/register/special-vote/index.html");
        registry.addViewController("/register/northern-region").setViewName("forward:/register/northern-region/index.html");
        registry.addViewController("/register/central-region").setViewName("forward:/register/central-region/index.html");
        registry.addViewController("/register/southern-region").setViewName("forward:/register/southern-region/index.html");
        registry.addViewController("/register/confirm-attendance").setViewName("forward:/register/confirm-attendance/index.html");
        registry.addViewController("/register/confirm-northern").setViewName("forward:/register/confirm-northern/index.html");
        registry.addViewController("/register/confirm-central").setViewName("forward:/register/confirm-central/index.html");
        registry.addViewController("/register/confirm-southern").setViewName("forward:/register/confirm-southern/index.html");
        registry.addViewController("/register/ticket").setViewName("forward:/register/ticket/index.html");

        // Add sub-routes for bmm
        registry.addViewController("/bmm/confirmation").setViewName("forward:/bmm/confirmation/index.html");
        registry.addViewController("/bmm/confirmation/").setViewName("forward:/bmm/confirmation/index.html");
        registry.addViewController("/bmm/preferences").setViewName("forward:/bmm/preferences/index.html");

        // Add venue routes
        registry.addViewController("/venue/bmm-checkin").setViewName("forward:/venue/bmm-checkin/index.html");
    }
}