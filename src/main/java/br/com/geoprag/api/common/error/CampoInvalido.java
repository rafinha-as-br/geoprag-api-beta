package br.com.geoprag.api.common.error;

/** Item de {@code errors[]} na resposta de erro de validação. */
public record CampoInvalido(String field, String message) {
}
