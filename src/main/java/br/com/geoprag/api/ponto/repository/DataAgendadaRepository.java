package br.com.geoprag.api.ponto.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import br.com.geoprag.api.ponto.domain.DataAgendada;

/** Todas as consultas recebem o {@code tenantId} (o do token); ver {@link PontoRepository}. */
public interface DataAgendadaRepository extends Repository<DataAgendada, UUID> {

    DataAgendada save(DataAgendada dataAgendada);

    Optional<DataAgendada> findByTenantIdAndId(String tenantId, UUID id);

    List<DataAgendada> findByTenantIdAndAgendamentoIdOrderByDataPrevista(String tenantId, UUID agendamentoId);
}
