package br.com.geoprag.api.ponto.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

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

    List<Ponto> findByTenantIdOrderByIdentificador(String tenantId);

    List<Ponto> findByTenantIdAndEstadoOrderByIdentificador(String tenantId, EstadoPonto estado);

    List<Ponto> findByTenantIdAndAplicadorIdOrderByIdentificador(String tenantId, UUID aplicadorId);
}
