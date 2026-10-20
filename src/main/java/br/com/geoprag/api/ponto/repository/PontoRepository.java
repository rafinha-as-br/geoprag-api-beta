package br.com.geoprag.api.ponto.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;

/**
 * Todas as consultas recebem o {@code tenantId} (o do token). Estende {@link Repository}, e não
 * {@code JpaRepository}, para não expor {@code findById}/{@code findAll} sem tenant nem {@code delete}
 * (um ponto nunca é excluído, apenas desativado).
 */
public interface PontoRepository extends Repository<Ponto, UUID> {

    Ponto save(Ponto ponto);

    Optional<Ponto> findByTenantIdAndId(String tenantId, UUID id);

    /**
     * Carrega o ponto com {@code SELECT … FOR UPDATE}: as mutações de estado do mesmo ponto se serializam e a
     * checagem da matriz vale até o commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Ponto p where p.tenant.id = :tenantId and p.id = :id")
    Optional<Ponto> findByTenantIdAndIdParaAtualizar(@Param("tenantId") String tenantId, @Param("id") UUID id);

    List<Ponto> findByTenantIdOrderByIdentificador(String tenantId);

    long countByTenantId(String tenantId);

    /** Pontos do tenant já com bairro e aplicador carregados (para a listagem, sem consulta extra por ponto). */
    @Query("select p from Ponto p join fetch p.bairro left join fetch p.aplicador "
            + "where p.tenant.id = :tenantId order by p.identificador")
    List<Ponto> findByTenantIdComBairroEAplicador(@Param("tenantId") String tenantId);

    /** Pontos do aplicador no estado pedido, já com bairro e aplicador carregados (pull do app). */
    @Query("select p from Ponto p join fetch p.bairro join fetch p.aplicador a "
            + "where p.tenant.id = :tenantId and a.id = :aplicadorId and p.estado = :estado "
            + "order by p.identificador")
    List<Ponto> findByTenantIdAndAplicadorIdAndEstadoComBairro(@Param("tenantId") String tenantId,
            @Param("aplicadorId") UUID aplicadorId, @Param("estado") EstadoPonto estado);

    /**
     * Pontos do aplicador no estado pedido que mudaram depois de {@code desde}: o próprio ponto, o agendamento, uma
     * data agendada ou um subponto registrado (pull incremental).
     */
    @Query("select distinct p from Ponto p join fetch p.bairro join fetch p.aplicador a "
            + "where p.tenant.id = :tenantId and a.id = :aplicadorId and p.estado = :estado and ("
            + "p.atualizadoEm > :desde "
            + "or exists (select 1 from Agendamento g where g.ponto = p and g.atualizadoEm > :desde) "
            + "or exists (select 1 from DataAgendada d where d.agendamento.ponto = p and d.atualizadoEm > :desde) "
            + "or exists (select 1 from Subponto s where s.aplicacao.ponto = p and s.recebidoEm > :desde)) "
            + "order by p.identificador")
    List<Ponto> findByTenantIdAndAplicadorIdAndEstadoAlteradosDesde(@Param("tenantId") String tenantId,
            @Param("aplicadorId") UUID aplicadorId, @Param("estado") EstadoPonto estado,
            @Param("desde") Instant desde);

    List<Ponto> findByTenantIdAndEstadoOrderByIdentificador(String tenantId, EstadoPonto estado);

    List<Ponto> findByTenantIdAndAplicadorIdOrderByIdentificador(String tenantId, UUID aplicadorId);
}
