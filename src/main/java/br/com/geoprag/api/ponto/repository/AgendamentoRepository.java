package br.com.geoprag.api.ponto.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import br.com.geoprag.api.ponto.domain.Agendamento;

/** Todas as consultas recebem o {@code tenantId} (o do token); ver {@link PontoRepository}. */
public interface AgendamentoRepository extends Repository<Agendamento, UUID> {

    Agendamento save(Agendamento agendamento);

    Optional<Agendamento> findByTenantIdAndPontoIdAndVigenteTrue(String tenantId, UUID pontoId);

    List<Agendamento> findByTenantIdAndPontoIdOrderByDataInicioDesc(String tenantId, UUID pontoId);

    /** Agendamentos (vigente e histórico) de vários pontos de uma vez. */
    List<Agendamento> findByTenantIdAndPontoIdIn(String tenantId, Collection<UUID> pontoIds);
}
