package br.com.geoprag.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/v1/auth/login}. O identificador é o CPF (com ou sem máscara) ou o e-mail. */
public record LoginRequest(
        @NotBlank @Size(max = 254) String identificador,
        @NotBlank @Size(max = 200) String senha) {

    /** Nunca imprime o identificador (pode ser CPF) nem a senha. */
    @Override
    public String toString() {
        return "LoginRequest[...]";
    }
}
