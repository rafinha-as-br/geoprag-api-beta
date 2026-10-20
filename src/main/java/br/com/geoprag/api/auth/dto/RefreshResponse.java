package br.com.geoprag.api.auth.dto;

/** Resposta de {@code POST /api/v1/auth/refresh}: o novo par de tokens (contrato v0). */
public record RefreshResponse(String accessToken, String refreshToken) {

    /** Nunca imprime os tokens. */
    @Override
    public String toString() {
        return "RefreshResponse[...]";
    }
}
