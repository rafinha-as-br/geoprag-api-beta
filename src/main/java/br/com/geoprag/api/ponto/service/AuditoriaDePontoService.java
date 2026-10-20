package br.com.geoprag.api.ponto.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.ponto.domain.AcaoDePonto;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.domain.PontoAuditoria;
import br.com.geoprag.api.ponto.repository.PontoAuditoriaRepository;

/**
 * Trilha de auditoria dos pontos. {@link #registrar} só roda <b>dentro</b> da transação da mudança
 * ({@code Propagation.MANDATORY}): se a gravação da auditoria falhar, a mudança é desfeita junto. A consulta
 * {@link #historico} é interna (sem endpoint HTTP no beta).
 */
@Service
public class AuditoriaDePontoService {

    static final int LIMITE_DETALHES = 2000;

    private final PontoAuditoriaRepository eventos;
    private final Clock relogio;

    public AuditoriaDePontoService(PontoAuditoriaRepository eventos, Clock relogio) {
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * @param ponto            o ponto já com a mudança aplicada (o estado e o aplicador "novos" saem dele)
     * @param estadoAnterior   estado do ponto antes da mudança ({@code null} na criação)
     * @param aplicadorAnterior aplicador antes da mudança, ou {@code null}
     * @param detalhes         conteúdo da mudança (só dados do ponto), ou {@code null}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String tenantId, Ponto ponto, UUID atorId, AcaoDePonto acao, EstadoPonto estadoAnterior,
            UUID aplicadorAnterior, String detalhes) {
        UUID aplicadorNovo = ponto.getAplicador() == null ? null : ponto.getAplicador().getId();
        eventos.save(new PontoAuditoria(tenantId, ponto.getId(), atorId, acao, estadoAnterior, ponto.getEstado(),
                aplicadorAnterior, aplicadorNovo, limitar(detalhes), Instant.now(relogio)));
    }

    @Transactional(readOnly = true)
    public List<EventoDeAuditoria> historico(String tenantId, UUID pontoId) {
        return eventos.findByTenantIdAndPontoIdOrderByOcorridoEmAscIdAsc(tenantId, pontoId).stream()
                .map(EventoDeAuditoria::de).toList();
    }

    private static String limitar(String detalhes) {
        if (detalhes == null || detalhes.length() <= LIMITE_DETALHES) {
            return detalhes;
        }
        return detalhes.substring(0, LIMITE_DETALHES - 3) + "...";
    }

    /** Evento da trilha, como devolvido pela consulta interna do histórico. */
    public record EventoDeAuditoria(AcaoDePonto acao, UUID atorId, EstadoPonto estadoAnterior,
            EstadoPonto estadoNovo, UUID aplicadorAnteriorId, UUID aplicadorNovoId, String detalhes,
            Instant ocorridoEm) {

        static EventoDeAuditoria de(PontoAuditoria e) {
            return new EventoDeAuditoria(e.getAcao(), e.getAtorId(), e.getEstadoAnterior(), e.getEstadoNovo(),
                    e.getAplicadorAnteriorId(), e.getAplicadorNovoId(), e.getDetalhes(), e.getOcorridoEm());
        }
    }
}
