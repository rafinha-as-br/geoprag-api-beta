package br.com.geoprag.api.sync.service;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.common.error.ApiException;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.repository.PontoAuditoriaRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;
import br.com.geoprag.api.ponto.service.PontoService;
import br.com.geoprag.api.produto.domain.Produto;
import br.com.geoprag.api.produto.repository.ProdutoRepository;
import br.com.geoprag.api.sync.config.SyncProperties;
import br.com.geoprag.api.sync.dto.PullResponse;
import br.com.geoprag.api.sync.dto.PullResponse.PontoDoAplicador;
import br.com.geoprag.api.sync.dto.PullResponse.ProdutoResponse;

/**
 * Pull do App Aplicador. Sem {@code desde}: snapshot completo dos pontos {@code ATIVA} atribuídos ao aplicador do
 * token, no tenant do token, com agendamento, datas e subpontos já registrados, e os produtos do tenant. Com
 * {@code desde}: só o que mudou depois dele (pontos alterados, produtos novos) e os {@code removidos}. O ponto que
 * deixa de aparecer (reatribuído, cancelado, desativado) é tratado pelo app como retirado do aplicador.
 */
@Service
@Transactional(readOnly = true)
public class PullService {

    private final PontoRepository pontos;
    private final ProdutoRepository produtos;
    private final PontoAuditoriaRepository auditoria;
    private final PontoService pontoService;
    private final SyncProperties propriedades;
    private final Clock relogio;

    public PullService(PontoRepository pontos, ProdutoRepository produtos, PontoAuditoriaRepository auditoria,
            PontoService pontoService, SyncProperties propriedades, Clock relogio) {
        this.pontos = pontos;
        this.produtos = produtos;
        this.auditoria = auditoria;
        this.pontoService = pontoService;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /** @param desde instante ISO-8601 com offset, ou {@code null}/vazio para o snapshot completo */
    public PullResponse pull(String tenantId, UUID aplicadorId, String desde) {
        Instant limite = desde == null || desde.isBlank() ? null : interpretar(desde);
        // O cursor é calculado ANTES de ler, e recua uma margem: o que mudar durante a leitura (ou numa transação
        // ainda não confirmada) volta no próximo pull em vez de se perder.
        OffsetDateTime cursor = OffsetDateTime.now(relogio).minusSeconds(propriedades.margemCursorSegundos())
                .truncatedTo(ChronoUnit.MICROS);

        List<Ponto> doAplicador = limite == null
                ? pontos.findByTenantIdAndAplicadorIdAndEstadoComBairro(tenantId, aplicadorId, EstadoPonto.ATIVA)
                : pontos.findByTenantIdAndAplicadorIdAndEstadoAlteradosDesde(tenantId, aplicadorId,
                        EstadoPonto.ATIVA, limite);
        List<PontoDoAplicador> pontosDoApp = pontoService.detalhar(tenantId, doAplicador).stream()
                .map(PontoDoAplicador::de).toList();
        List<Produto> produtosLidos = limite == null ? produtos.findByTenantIdOrderByNome(tenantId)
                : produtos.findByTenantIdAndCriadoEmGreaterThanOrderByNome(tenantId, limite);
        List<ProdutoResponse> produtosDoApp = produtosLidos.stream()
                .map(p -> new ProdutoResponse(p.getId().toString(), p.getNome())).toList();

        return new PullResponse(pontosDoApp, produtosDoApp, cursor,
                limite == null ? List.of() : removidos(tenantId, aplicadorId, limite));
    }

    /**
     * Pontos que o aplicador tinha como ativos e deixou de ter desde {@code limite} (trilha de auditoria), menos os
     * que hoje voltaram a ser ativos dele: esses vão em {@code pontos}, nunca em {@code removidos}.
     */
    private List<String> removidos(String tenantId, UUID aplicadorId, Instant limite) {
        List<UUID> retirados = auditoria.findByTenantIdPontosRetiradosDoAplicadorDesde(tenantId, aplicadorId,
                EstadoPonto.ATIVA, limite);
        if (retirados.isEmpty()) {
            return List.of();
        }
        Set<UUID> aindaAtivos = new HashSet<>();
        pontos.findByTenantIdAndAplicadorIdAndEstadoComBairro(tenantId, aplicadorId, EstadoPonto.ATIVA)
                .forEach(p -> aindaAtivos.add(p.getId()));
        return retirados.stream().filter(id -> !aindaAtivos.contains(id)).map(UUID::toString).sorted().toList();
    }

    /** O {@code +} do offset costuma chegar como espaço quando o cliente não o codifica; um espaço nunca é válido. */
    private static Instant interpretar(String desde) {
        try {
            return OffsetDateTime.parse(desde.strip().replace(' ', '+')).toInstant();
        } catch (DateTimeParseException e) {
            throw ApiException.validacao("desde",
                    "Informe uma data e hora ISO-8601 com offset, por exemplo 2026-10-21T08:00:00-03:00.");
        }
    }
}
