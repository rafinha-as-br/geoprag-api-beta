package br.com.geoprag.api.sync.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Resposta de {@code POST /api/v1/sync/aplicacoes}: o instante do servidor e um resultado por item, na mesma
 * ordem do envio. Sem informação de conflito: o aplicador não vê o conflito.
 */
public record RespostaDoLote(OffsetDateTime recebidoEm, List<ResultadoDoItem> resultados) {
}
