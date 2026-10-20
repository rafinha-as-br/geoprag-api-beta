package br.com.geoprag.api.ponto.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corpo de {@code POST /pontos/{id}/ativacao}: o servidor gera as datas a partir destes três campos. */
public record AtivarPontoRequest(
        @NotNull(message = "Informe a data da primeira aplicação.") LocalDate dataInicio,
        @NotNull(message = "Informe o intervalo em dias.")
        @Min(value = 1, message = "O intervalo deve ser de pelo menos 1 dia.") Integer intervaloDias,
        @NotNull(message = "Informe a quantidade de recorrências.")
        @Min(value = 1, message = "A quantidade de recorrências deve ser de pelo menos 1.")
        Integer quantidadeRecorrencias) {
}
