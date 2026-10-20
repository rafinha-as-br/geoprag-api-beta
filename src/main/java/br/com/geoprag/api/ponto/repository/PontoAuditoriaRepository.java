package br.com.geoprag.api.ponto.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.PontoAuditoria;

/**
 * Trilha de auditoria: só grava e consulta (append-only). Estende {@link Repository}, e não {@code JpaRepository},
 * para não expor {@code delete} nem consultas sem tenant.
 */
public interface PontoAuditoriaRepository extends Repository<PontoAuditoria, Long> {

    PontoAuditoria save(PontoAuditoria evento);

    /** Histórico do ponto em ordem cronológica (o {@code id} desempata eventos no mesmo instante). */
    List<PontoAuditoria> findByTenantIdAndPontoIdOrderByOcorridoEmAscIdAsc(String tenantId, UUID pontoId);

    /**
     * Pontos que o aplicador tinha como {@code ATIVA} e deixou de ter depois de {@code desde} (cancelamento,
     * desativação ou troca de aplicador): o evento parte de {@code ATIVA} com esse aplicador e termina em outro estado
     * ou com outro aplicador. Base dos {@code removidos} do pull incremental.
     */
    @Query("select distinct e.pontoId from PontoAuditoria e where e.tenantId = :tenantId "
            + "and e.aplicadorAnteriorId = :aplicadorId and e.estadoAnterior = :ativa and e.ocorridoEm > :desde "
            + "and (e.estadoNovo <> :ativa or e.aplicadorNovoId is null or e.aplicadorNovoId <> :aplicadorId)")
    List<UUID> findByTenantIdPontosRetiradosDoAplicadorDesde(@Param("tenantId") String tenantId,
            @Param("aplicadorId") UUID aplicadorId, @Param("ativa") EstadoPonto ativa, @Param("desde") Instant desde);
}
