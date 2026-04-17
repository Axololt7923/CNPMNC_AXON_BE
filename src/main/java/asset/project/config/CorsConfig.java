package asset.project.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS configuration for the REST API.
 *
 * Dev: allows all origins (frontend on localhost:3000, Swagger UI, Postman)
 * Prod: restrict to allowed origins via ${cors.allowed-origins} env var
 *
 * Property example (application-prod.properties):
 *   cors.allowed-origins=https://pos.restaurant.example.com,https://admin.restaurant.example.com
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:8080}")
    private List<String> allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allowed origins (from property — override in prod)
        config.setAllowedOrigins(allowedOrigins);

        // HTTP methods allowed from browser
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Headers client is allowed to send
        config.setAllowedHeaders(List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-User-Id",        // custom header used by some endpoints
            "X-Requested-With",
            "X-Refresh-Token"
        ));

        // Headers browser is allowed to read from response
        config.setExposedHeaders(List.of(
            "Authorization",
            "Content-Disposition" // for file download responses
        ));

        // Allow cookies / Authorization header in cross-origin requests
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour (reduces OPTIONS round-trips)
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
