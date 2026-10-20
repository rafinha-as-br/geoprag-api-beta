package br.com.geoprag.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/v1/auth/refresh}. */
public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {

    /** Nunca imprime o token. */
    @Override
    public String toString() {
        return "RefreshRequest[...]";
    }
}
