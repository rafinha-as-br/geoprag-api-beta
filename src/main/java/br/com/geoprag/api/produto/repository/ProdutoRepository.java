package br.com.geoprag.api.produto.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import br.com.geoprag.api.produto.domain.Produto;

/**
 * Todas as consultas recebem o {@code tenantId} (o do token). Estende {@link Repository}, e não
 * {@code JpaRepository}, para não expor {@code findById}/{@code findAll} sem tenant.
 */
public interface ProdutoRepository extends Repository<Produto, UUID> {

    Produto save(Produto produto);

    Optional<Produto> findByTenantIdAndId(String tenantId, UUID id);

    List<Produto> findByTenantIdOrderByNome(String tenantId);
}
