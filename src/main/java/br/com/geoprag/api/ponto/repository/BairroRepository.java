package br.com.geoprag.api.ponto.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import br.com.geoprag.api.ponto.domain.Bairro;

/**
 * Todas as consultas recebem o {@code tenantId} (o do token). Estende {@link Repository}, e não
 * {@code JpaRepository}, para não expor {@code findById}/{@code findAll} sem tenant.
 */
public interface BairroRepository extends Repository<Bairro, UUID> {

    Bairro save(Bairro bairro);

    Optional<Bairro> findByTenantIdAndId(String tenantId, UUID id);

    List<Bairro> findByTenantIdOrderByNome(String tenantId);
}
