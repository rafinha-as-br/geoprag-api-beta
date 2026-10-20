package br.com.geoprag.api.ponto.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import br.com.geoprag.api.ponto.domain.PontoAuditoria;

/**
 * Trilha de auditoria: só grava e consulta (append-only). Estende {@link Repository}, e não {@code JpaRepository},
 * para não expor {@code delete} nem consultas sem tenant.
 */
public interface PontoAuditoriaRepository extends Repository<PontoAuditoria, Long> {

    PontoAuditoria save(PontoAuditoria evento);

    /** Histórico do ponto em ordem cronológica (o {@code id} desempata eventos no mesmo instante). */
    List<PontoAuditoria> findByTenantIdAndPontoIdOrderByOcorridoEmAscIdAsc(String tenantId, UUID pontoId);
}
