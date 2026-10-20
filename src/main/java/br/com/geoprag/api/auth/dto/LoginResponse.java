package br.com.geoprag.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resposta de {@code POST /api/v1/auth/login}. O {@code refreshToken} só vem para o perfil {@code APLICADOR}
 * (para os demais o campo é omitido).
 */
public record LoginResponse(String accessToken, long expiresInSeconds,
        @JsonInclude(JsonInclude.Include.NON_NULL) String refreshToken, UsuarioResponse usuario) {

    /** Nunca imprime os tokens. */
    @Override
    public String toString() {
        return "LoginResponse[...]";
    }
}
