package br.com.geoprag.api.auth.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Leitura dos dados do JWT validado. O tenant e o usuário vêm SEMPRE do token, nunca de parâmetro da requisição.
 * (O filtro de segurança já garante que o token tem {@code tenantId} e perfil válidos.)
 */
public final class DadosDoToken {

    private DadosDoToken() {
    }

    public static String tenantId(Jwt token) {
        return token.getClaimAsString("tenantId");
    }

    public static UUID usuarioId(Jwt token) {
        return UUID.fromString(token.getSubject());
    }
}
