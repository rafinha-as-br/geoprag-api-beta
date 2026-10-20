package br.com.geoprag.api.ponto.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.common.error.ApiException;
import br.com.geoprag.api.common.error.ErrorCode;
import br.com.geoprag.api.ponto.domain.Agendamento;
import br.com.geoprag.api.ponto.domain.DataAgendada;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.dto.PontoResponse;
import br.com.geoprag.api.ponto.repository.AgendamentoRepository;
import br.com.geoprag.api.ponto.repository.DataAgendadaRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;
import br.com.geoprag.api.sync.domain.Subponto;
import br.com.geoprag.api.sync.repository.SubpontoRepository;

/**
 * Leitura de pontos para o portal. Tudo restrito ao tenant do token: ponto de outro tenant (ou id inexistente ou
 * malformado) é {@code 404 PONTO_NAO_ENCONTRADO}, nunca 403, para não revelar a existência.
 */
@Service
@Transactional(readOnly = true)
public class PontoService {

    private static final Pattern UUID_CANONICO =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final PontoRepository pontos;
    private final AgendamentoRepository agendamentos;
    private final DataAgendadaRepository datas;
    private final SubpontoRepository subpontos;

    public PontoService(PontoRepository pontos, AgendamentoRepository agendamentos, DataAgendadaRepository datas,
            SubpontoRepository subpontos) {
        this.pontos = pontos;
        this.agendamentos = agendamentos;
        this.datas = datas;
        this.subpontos = subpontos;
    }

    /**
     * Pontos do tenant, ordenados pelo identificador, com os filtros simples opcionais: {@code bairro} (nome do
     * bairro, sem diferenciar maiúsculas) e {@code estado}.
     */
    public List<PontoResponse> listar(String tenantId, String bairro, EstadoPonto estado) {
        String nomeDoBairro = bairro == null || bairro.isBlank() ? null : bairro.trim();
        List<Ponto> filtrados = pontos.findByTenantIdComBairroEAplicador(tenantId).stream()
                .filter(p -> estado == null || p.getEstado() == estado)
                .filter(p -> nomeDoBairro == null || p.getBairro().getNome().equalsIgnoreCase(nomeDoBairro))
                .toList();
        return montar(tenantId, filtrados);
    }

    public PontoResponse obter(String tenantId, String id) {
        UUID pontoId = uuid(id).orElseThrow(PontoService::naoEncontrado);
        Ponto ponto = pontos.findByTenantIdAndId(tenantId, pontoId).orElseThrow(PontoService::naoEncontrado);
        return montar(tenantId, List.of(ponto)).get(0);
    }

    /** Carrega agendamentos, datas e registros dos pontos em lote (poucas consultas, não uma por ponto). */
    /** Resposta completa de vários pontos já carregados (carga em lote, sem consulta por ponto). */
    public List<PontoResponse> detalhar(String tenantId, List<Ponto> pontos) {
        return montar(tenantId, pontos);
    }

    /** Resposta completa de um ponto já carregado (usada depois de uma mutação, na mesma transação). */
    PontoResponse detalhar(String tenantId, Ponto ponto) {
        return montar(tenantId, List.of(ponto)).get(0);
    }

    private List<PontoResponse> montar(String tenantId, List<Ponto> lista) {
        if (lista.isEmpty()) {
            return List.of();
        }
        List<UUID> pontoIds = lista.stream().map(Ponto::getId).toList();
        Map<UUID, Agendamento> agendamentoDoPonto = agendamentos.findByTenantIdAndPontoIdIn(tenantId, pontoIds)
                .stream().collect(Collectors.groupingBy(a -> a.getPonto().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), PontoService::maisRelevante)));
        Collection<UUID> agendamentoIds = agendamentoDoPonto.values().stream().map(Agendamento::getId).toList();
        Map<UUID, List<DataAgendada>> datasDoAgendamento = agendamentoIds.isEmpty() ? Map.of()
                : datas.findByTenantIdAndAgendamentoIdInOrderByDataPrevista(tenantId, agendamentoIds).stream()
                        .collect(Collectors.groupingBy(d -> d.getAgendamento().getId()));
        Map<UUID, List<Subponto>> subpontosDoPonto = subpontos
                .findByTenantIdComAplicacaoDosPontos(tenantId, pontoIds).stream()
                .collect(Collectors.groupingBy(s -> s.getAplicacao().getPonto().getId()));

        return lista.stream().map(ponto -> {
            Agendamento agendamento = agendamentoDoPonto.get(ponto.getId());
            List<DataAgendada> datasDoPonto = agendamento == null ? List.of()
                    : datasDoAgendamento.getOrDefault(agendamento.getId(), List.of());
            return PontoResponse.de(ponto, agendamento, datasDoPonto,
                    subpontosDoPonto.getOrDefault(ponto.getId(), List.of()));
        }).toList();
    }

    /** O agendamento vigente; sem vigente (ex.: ponto inativo), o mais recente, que guarda o histórico. */
    private static Agendamento maisRelevante(List<Agendamento> doPonto) {
        return doPonto.stream().filter(Agendamento::isVigente).findFirst()
                .orElseGet(() -> doPonto.stream().max(Comparator.comparing(Agendamento::getDataInicio)
                        .thenComparing(Agendamento::getCriadoEm)).orElseThrow());
    }

    static Optional<UUID> uuid(String texto) {
        if (texto == null || !UUID_CANONICO.matcher(texto.trim()).matches()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(texto.trim()));
    }

    static ApiException naoEncontrado() {
        return new ApiException(ErrorCode.PONTO_NAO_ENCONTRADO, HttpStatus.NOT_FOUND);
    }
}
