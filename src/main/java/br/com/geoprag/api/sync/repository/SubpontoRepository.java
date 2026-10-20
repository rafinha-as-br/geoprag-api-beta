package br.com.geoprag.api.sync.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import br.com.geoprag.api.sync.domain.Subponto;

/**
 * Todas as consultas recebem o {@code tenantId} (o do token), com uma única exceção, {@link #existsById}, usada só
 * para detectar colisão da chave primária (o UUID do app é global). Sem {@code delete}: registro append-only.
 */
public interface SubpontoRepository extends Repository<Subponto, UUID> {

    Subponto save(Subponto subponto);

    Optional<Subponto> findByTenantIdAndId(String tenantId, UUID id);

    long countByTenantIdAndAplicacaoId(String tenantId, UUID aplicacaoId);

    /** Quantos subpontos já foram registrados no ponto (qualquer aplicação). */
    long countByTenantIdAndAplicacaoPontoId(String tenantId, UUID pontoId);

    /** Registros de um ponto, com a marca de conflito (para o detalhe do ponto, visível ao gestor). */
    List<Subponto> findByTenantIdAndAplicacaoPontoIdOrderByRealizadoEm(String tenantId, UUID pontoId);

    /** Registros de vários pontos de uma vez, com a aplicação carregada (para montar o detalhe sem N+1). */
    @Query("select s from Subponto s join fetch s.aplicacao a "
            + "where s.tenant.id = :tenantId and a.ponto.id in :pontoIds order by s.realizadoEm")
    List<Subponto> findByTenantIdComAplicacaoDosPontos(@Param("tenantId") String tenantId,
            @Param("pontoIds") Collection<UUID> pontoIds);

    /** Sem tenant: só para distinguir "id já usado em outro tenant" de "id novo". Nunca devolver o registro. */
    boolean existsById(UUID id);
}
