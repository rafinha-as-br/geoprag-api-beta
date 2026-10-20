package br.com.geoprag.api.sync.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.dto.PontoResponse;
import br.com.geoprag.api.ponto.dto.PontoResponse.AgendamentoResponse;

/**
 * Resposta de {@code GET /api/v1/sync/pull} (snapshot completo do contrato v0): os pontos atribuídos ao aplicador e os
 * produtos disponíveis. Sem cursor nem {@code removidos} (pull incremental é da GEOPRAG-205).
 */
public record PullResponse(List<PontoDoAplicador> pontos, List<ProdutoResponse> produtos) {

    /**
     * Mesmo formato de {@code Ponto}, mas os subpontos <b>não</b> trazem a marca de conflito: o aplicador não vê o
     * conflito (visível só ao gestor no portal).
     */
    public record PontoDoAplicador(
            String id,
            String identificador,
            String nome,
            String bairro,
            String bairroId,
            String endereco,
            String numeroReferencia,
            String descricaoDoTrecho,
            BigDecimal larguraMetros,
            BigDecimal profundidadeMetros,
            BigDecimal velocidadeMetrosPorSegundo,
            BigDecimal dosagemMl,
            BigDecimal distanciaEntreSubpontosMetros,
            int quantidadeDeSubpontos,
            int toleranciaAtrasoDias,
            String aplicadorId,
            EstadoPonto estado,
            EstadoPonto estadoAnterior,
            AgendamentoResponse agendamento,
            List<SubpontoDoAplicador> subpontos) {

        public static PontoDoAplicador de(PontoResponse p) {
            return new PontoDoAplicador(p.id(), p.identificador(), p.nome(), p.bairro(), p.bairroId(), p.endereco(),
                    p.numeroReferencia(), p.descricaoDoTrecho(), p.larguraMetros(), p.profundidadeMetros(),
                    p.velocidadeMetrosPorSegundo(), p.dosagemMl(), p.distanciaEntreSubpontosMetros(),
                    p.quantidadeDeSubpontos(), p.toleranciaAtrasoDias(), p.aplicadorId(), p.estado(),
                    p.estadoAnterior(), p.agendamento(),
                    p.subpontos().stream().map(s -> new SubpontoDoAplicador(s.id(), s.latitude(), s.longitude(),
                            s.realizadoEm(), s.recebidoEm())).toList());
        }
    }

    public record SubpontoDoAplicador(String id, BigDecimal latitude, BigDecimal longitude,
            OffsetDateTime realizadoEm, OffsetDateTime recebidoEm) {
    }

    public record ProdutoResponse(String id, String nome) {
    }
}
