package br.com.geoprag.api.auth.dto;

/** Resposta de {@code POST /api/v1/auth/login}. O {@code refreshToken} do aplicador entra com a GEOPRAG-184. */
public record LoginResponse(String accessToken, long expiresInSeconds, UsuarioResponse usuario) {
}
