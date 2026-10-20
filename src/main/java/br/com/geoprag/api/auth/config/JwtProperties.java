package br.com.geoprag.api.auth.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT, vinda de {@code JWT_SECRET} e {@code JWT_ACCESS_TOKEN_MINUTES}.
 *
 * <p>Fail-fast: a aplicação não sobe com segredo ausente ou fraco (HS256 exige ao menos 256 bits). A mensagem
 * de erro nunca inclui o valor do segredo.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, int accessTokenMinutes) {

    /** HS256: chave de, no mínimo, 256 bits. */
    static final int TAMANHO_MINIMO_SEGREDO_EM_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()
                || secret.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO_EM_BYTES) {
            throw new IllegalStateException("JWT_SECRET ausente ou fraco: defina um segredo aleatório com pelo menos "
                    + TAMANHO_MINIMO_SEGREDO_EM_BYTES + " caracteres (veja .env.example).");
        }
        if (accessTokenMinutes <= 0) {
            throw new IllegalStateException("JWT_ACCESS_TOKEN_MINUTES deve ser maior que zero.");
        }
    }
}
