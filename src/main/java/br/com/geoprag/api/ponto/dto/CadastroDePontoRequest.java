package br.com.geoprag.api.ponto.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /pontos} (cadastro) e, sem {@code aplicadorId}, de {@code PUT /pontos/{id}} (o corpo
 * substitui o cadastro: opcional omitido fica sem valor e {@code toleranciaAtrasoDias} omitido vale 4). As medidas
 * seguem {@code NUMERIC(8, 2)} do esquema.
 */
public record CadastroDePontoRequest(
        @NotBlank(message = "Informe o nome do ponto.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.") String nome,
        @NotBlank(message = "Informe o bairro.") String bairroId,
        @NotBlank(message = "Informe o endereço.")
        @Size(max = 200, message = "O endereço deve ter no máximo 200 caracteres.") String endereco,
        @Size(max = 30, message = "A referência deve ter no máximo 30 caracteres.") String numeroReferencia,
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres.") String descricaoDoTrecho,
        @Positive(message = "A largura deve ser maior que zero.")
        @Digits(integer = 6, fraction = 2, message = "A largura aceita até 2 casas decimais.")
        BigDecimal larguraMetros,
        @Positive(message = "A profundidade deve ser maior que zero.")
        @Digits(integer = 6, fraction = 2, message = "A profundidade aceita até 2 casas decimais.")
        BigDecimal profundidadeMetros,
        @Positive(message = "A velocidade deve ser maior que zero.")
        @Digits(integer = 6, fraction = 2, message = "A velocidade aceita até 2 casas decimais.")
        BigDecimal velocidadeMetrosPorSegundo,
        @NotNull(message = "Informe a dosagem.")
        @Positive(message = "A dosagem deve ser maior que zero.")
        @Digits(integer = 6, fraction = 2, message = "A dosagem aceita até 2 casas decimais.")
        BigDecimal dosagemMl,
        @NotNull(message = "Informe a distância entre os subpontos.")
        @Positive(message = "A distância deve ser maior que zero.")
        @Digits(integer = 6, fraction = 2, message = "A distância aceita até 2 casas decimais.")
        BigDecimal distanciaEntreSubpontosMetros,
        @NotNull(message = "Informe a quantidade de subpontos.")
        @Min(value = 1, message = "A quantidade de subpontos deve ser de pelo menos 1.") Integer quantidadeDeSubpontos,
        @Min(value = 0, message = "A tolerância não pode ser negativa.") Integer toleranciaAtrasoDias,
        String aplicadorId) {
}
