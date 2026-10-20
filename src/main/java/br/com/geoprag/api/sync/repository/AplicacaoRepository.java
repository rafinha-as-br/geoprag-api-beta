package br.com.geoprag.api.sync.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import br.com.geoprag.api.sync.domain.Aplicacao;

/** Todas as consultas recebem o {@code tenantId} (o do token); sem {@code delete} (registro append-only). */
public interface AplicacaoRepository extends Repository<Aplicacao, UUID> {

    Aplicacao save(Aplicacao aplicacao);

    Optional<Aplicacao> findByTenantIdAndId(String tenantId, UUID id);

    /** Já existe outra aplicação (com outro id) para a mesma data agendada? */
    boolean existsByTenantIdAndDataAgendadaIdAndIdNot(String tenantId, UUID dataAgendadaId, UUID aplicacaoId);
}
