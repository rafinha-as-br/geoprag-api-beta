package br.com.geoprag.api.sync.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Item enviado pelo app (um subponto), como no contrato v0. Os ids chegam como texto e as coordenadas e a data
 * podem vir nulas: a validação é do serviço, que rejeita só o item inválido (sem derrubar o lote). Não há
 * {@code recebidoEm}: quem define é o servidor.
 */
public record ItemDeRegistro(String id, String aplicacaoId, String pontoId, String dataAgendadaId,
        String produtoId, BigDecimal latitude, BigDecimal longitude, OffsetDateTime realizadoEm) {
}
