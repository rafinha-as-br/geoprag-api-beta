package br.com.geoprag.api.common.cors;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origens permitidas para CORS, vindas de {@code CORS_ALLOWED_ORIGINS} (lista separada por vírgula).
 * Vazio significa que nenhuma origem externa é permitida.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of()
                : allowedOrigins.stream().map(String::trim).filter(o -> !o.isEmpty()).toList();
    }
}
