package br.com.geoprag.api.ponto.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import br.com.geoprag.api.ponto.domain.Agendamento;
import br.com.geoprag.api.ponto.domain.DataAgendada;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.domain.StatusDataAgendada;
import br.com.geoprag.api.sync.domain.MotivoConflito;
import br.com.geoprag.api.sync.domain.Subponto;

/**
 * Modelo {@code Ponto} do contrato v0 (com a extensão {@code bairroId} e {@code toleranciaAtrasoDias}). Não
 * expõe o {@code tenantId} (vem do token) nem a vazão (derivada).
 */
public record PontoResponse(
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
        List<SubpontoResponse> subpontos) {

    public static PontoResponse de(Ponto ponto, Agendamento agendamento, List<DataAgendada> datas,
            List<Subponto> subpontos) {
        return new PontoResponse(
                ponto.getId().toString(),
                ponto.getIdentificador(),
                ponto.getNome(),
                ponto.getBairro().getNome(),
                ponto.getBairro().getId().toString(),
                ponto.getEndereco(),
                ponto.getNumeroReferencia(),
                ponto.getDescricaoDoTrecho(),
                ponto.getLarguraMetros(),
                ponto.getProfundidadeMetros(),
                ponto.getVelocidadeMetrosPorSegundo(),
                ponto.getDosagemMl(),
                ponto.getDistanciaEntreSubpontosMetros(),
                ponto.getQuantidadeDeSubpontos(),
                ponto.getToleranciaAtrasoDias(),
                ponto.getAplicador() == null ? null : ponto.getAplicador().getId().toString(),
                ponto.getEstado(),
                ponto.getEstadoAnterior(),
                agendamento == null ? null : AgendamentoResponse.de(agendamento, datas),
                subpontos.stream().map(SubpontoResponse::de).toList());
    }

    /** Agendamento do ponto, com as datas geradas pelo servidor. */
    public record AgendamentoResponse(LocalDate dataInicio, int intervaloDias, int quantidadeRecorrencias,
            List<DataAgendadaResponse> datas) {

        static AgendamentoResponse de(Agendamento agendamento, List<DataAgendada> datas) {
            return new AgendamentoResponse(agendamento.getDataInicio(), agendamento.getIntervaloDias(),
                    agendamento.getQuantidadeRecorrencias(), datas.stream().map(DataAgendadaResponse::de).toList());
        }
    }

    public record DataAgendadaResponse(String id, LocalDate data, StatusDataAgendada status) {

        static DataAgendadaResponse de(DataAgendada data) {
            return new DataAgendadaResponse(data.getId().toString(), data.getDataPrevista(), data.getStatus());
        }
    }

    /**
     * Registro de aplicação já recebido. {@code conflito} e {@code motivoConflito} são <b>extensão</b> sobre o
     * contrato v0 (campos opcionais, visíveis só aos perfis de administração): a marca de conflito fica consultável
     * no detalhe do ponto, como pedem as GEOPRAG-193 e 199. Datas em UTC com offset.
     */
    public record SubpontoResponse(String id, BigDecimal latitude, BigDecimal longitude, OffsetDateTime realizadoEm,
            OffsetDateTime recebidoEm, boolean conflito, MotivoConflito motivoConflito) {

        static SubpontoResponse de(Subponto subponto) {
            return new SubpontoResponse(subponto.getId().toString(), subponto.getLatitude(),
                    subponto.getLongitude(), subponto.getRealizadoEm().atOffset(ZoneOffset.UTC),
                    subponto.getRecebidoEm().atOffset(ZoneOffset.UTC), subponto.isConflito(),
                    subponto.getMotivoConflito());
        }
    }
}
