package br.com.geoprag.api.sync.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.repository.PontoRepository;
import br.com.geoprag.api.ponto.service.PontoService;
import br.com.geoprag.api.produto.repository.ProdutoRepository;
import br.com.geoprag.api.sync.dto.PullResponse;
import br.com.geoprag.api.sync.dto.PullResponse.PontoDoAplicador;
import br.com.geoprag.api.sync.dto.PullResponse.ProdutoResponse;

/**
 * Snapshot completo para o App Aplicador: os pontos {@code ATIVA} atribuídos ao aplicador do token, no tenant do
 * token, com agendamento, datas e subpontos já registrados, e os produtos do tenant. O ponto que deixa de aparecer
 * (reatribuído, cancelado, desativado) é tratado pelo app como retirado do aplicador.
 */
@Service
@Transactional(readOnly = true)
public class PullService {

    private final PontoRepository pontos;
    private final ProdutoRepository produtos;
    private final PontoService pontoService;

    public PullService(PontoRepository pontos, ProdutoRepository produtos, PontoService pontoService) {
        this.pontos = pontos;
        this.produtos = produtos;
        this.pontoService = pontoService;
    }

    public PullResponse snapshot(String tenantId, UUID aplicadorId) {
        List<Ponto> doAplicador = pontos.findByTenantIdAndAplicadorIdAndEstadoComBairro(tenantId, aplicadorId,
                EstadoPonto.ATIVA);
        List<PontoDoAplicador> pontosDoApp = pontoService.detalhar(tenantId, doAplicador).stream()
                .map(PontoDoAplicador::de).toList();
        List<ProdutoResponse> produtosDoApp = produtos.findByTenantIdOrderByNome(tenantId).stream()
                .map(p -> new ProdutoResponse(p.getId().toString(), p.getNome())).toList();
        return new PullResponse(pontosDoApp, produtosDoApp);
    }
}
