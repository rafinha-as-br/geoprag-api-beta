package br.com.geoprag.api.ponto.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /pontos/{id}/aplicador}. */
public record AtribuirAplicadorRequest(@NotBlank(message = "Informe o aplicador.") String aplicadorId) {
}
