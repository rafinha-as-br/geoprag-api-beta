package br.com.geoprag.api.ponto.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.auth.domain.Tenant;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.repository.TenantRepository;
import br.com.geoprag.api.common.error.ApiException;
import br.com.geoprag.api.common.error.ErrorCode;
import br.com.geoprag.api.ponto.domain.AcaoDePonto;
import br.com.geoprag.api.ponto.domain.Bairro;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.dto.CadastroDePontoRequest;
import br.com.geoprag.api.ponto.dto.PontoResponse;
import br.com.geoprag.api.ponto.repository.AgendamentoRepository;
import br.com.geoprag.api.ponto.repository.BairroRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;
import br.com.geoprag.api.sync.repository.SubpontoRepository;

/**
 * Cadastro e edição de pontos pelo administrador. O tenant vem do token. O {@code identificador} ({@code PA-001},
 * {@code PA-002}, …) é gerado aqui, sob {@code SELECT … FOR UPDATE} na linha do tenant: pontos nunca são
 * excluídos, então a contagem + 1 não se repete.
 */
@Service
public class CadastroDePontoService {

    static final int TOLERANCIA_PADRAO_DIAS = Ponto.TOLERANCIA_ATRASO_PADRAO_DIAS;
    static final String MOTIVO_EDICAO_BLOQUEADA = "O cadastro do ponto só pode ser alterado enquanto ele não tem "
            + "agendamento vigente nem aplicação registrada. Nome e tolerância de atraso podem ser alterados a "
            + "qualquer momento.";

    private final PontoRepository pontos;
    private final TenantRepository tenants;
    private final BairroRepository bairros;
    private final AplicadorDoTenant aplicadores;
    private final AgendamentoRepository agendamentos;
    private final SubpontoRepository subpontos;
    private final PontoService leitura;
    private final AuditoriaDePontoService auditoria;

    public CadastroDePontoService(PontoRepository pontos, TenantRepository tenants, BairroRepository bairros,
            AplicadorDoTenant aplicadores, AgendamentoRepository agendamentos, SubpontoRepository subpontos,
            PontoService leitura, AuditoriaDePontoService auditoria) {
        this.pontos = pontos;
        this.tenants = tenants;
        this.bairros = bairros;
        this.aplicadores = aplicadores;
        this.agendamentos = agendamentos;
        this.subpontos = subpontos;
        this.leitura = leitura;
        this.auditoria = auditoria;
    }

    @Transactional
    public PontoResponse criar(String tenantId, UUID atorId, CadastroDePontoRequest corpo) {
        Tenant tenant = tenants.findByIdParaAtualizar(tenantId)
                .orElseThrow(() -> new ApiException(ErrorCode.NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED));
        Bairro bairro = bairroDoTenant(tenantId, corpo.bairroId());
        Usuario aplicador = corpo.aplicadorId() == null || corpo.aplicadorId().isBlank() ? null
                : aplicadores.resolver(tenantId, corpo.aplicadorId());
        String identificador = String.format("PA-%03d", pontos.countByTenantId(tenantId) + 1);
        Ponto.Cadastro cadastro = cadastro(corpo, bairro);
        Ponto ponto = new Ponto(tenant, identificador, cadastro.nome(), bairro, cadastro.endereco(),
                cadastro.dosagemMl(), cadastro.distanciaEntreSubpontosMetros(), cadastro.quantidadeDeSubpontos(),
                aplicador);
        ponto.aplicarCadastro(cadastro);
        pontos.save(ponto);
        auditoria.registrar(tenantId, ponto, atorId, AcaoDePonto.CRIACAO, null, null, "identificador=" + identificador);
        return leitura.detalhar(tenantId, ponto);
    }

    @Transactional
    public PontoResponse editar(String tenantId, UUID atorId, String pontoId, CadastroDePontoRequest corpo) {
        UUID id = PontoService.uuid(pontoId).orElseThrow(PontoService::naoEncontrado);
        Ponto ponto = pontos.findByTenantIdAndIdParaAtualizar(tenantId, id).orElseThrow(PontoService::naoEncontrado);
        Ponto.Cadastro novo = cadastro(corpo, bairroDoTenant(tenantId, corpo.bairroId()));
        if (ponto.alteraCadastroCompleto(novo) && temAgendamentoVigenteOuExecucao(tenantId, ponto)) {
            throw ApiException.operacaoNaoPermitida(MOTIVO_EDICAO_BLOQUEADA);
        }
        List<String> diferencas = ponto.diferencas(novo);
        ponto.aplicarCadastro(novo);
        if (!diferencas.isEmpty()) {
            auditoria.registrar(tenantId, ponto, atorId, AcaoDePonto.EDICAO, ponto.getEstado(),
                    ponto.getAplicador() == null ? null : ponto.getAplicador().getId(),
                    String.join("; ", diferencas));
        }
        return leitura.detalhar(tenantId, ponto);
    }

    private boolean temAgendamentoVigenteOuExecucao(String tenantId, Ponto ponto) {
        return agendamentos.findByTenantIdAndPontoIdAndVigenteTrue(tenantId, ponto.getId()).isPresent()
                || subpontos.countByTenantIdAndAplicacaoPontoId(tenantId, ponto.getId()) > 0;
    }

    private Bairro bairroDoTenant(String tenantId, String bairroId) {
        return PontoService.uuid(bairroId).flatMap(id -> bairros.findByTenantIdAndId(tenantId, id))
                .orElseThrow(() -> ApiException.validacao("bairroId", "Informe um bairro válido."));
    }

    private static Ponto.Cadastro cadastro(CadastroDePontoRequest corpo, Bairro bairro) {
        return new Ponto.Cadastro(corpo.nome().strip(), bairro, corpo.endereco().strip(),
                textoOuNulo(corpo.numeroReferencia()), textoOuNulo(corpo.descricaoDoTrecho()),
                corpo.larguraMetros(), corpo.profundidadeMetros(), corpo.velocidadeMetrosPorSegundo(),
                corpo.dosagemMl(), corpo.distanciaEntreSubpontosMetros(), corpo.quantidadeDeSubpontos(),
                corpo.toleranciaAtrasoDias() == null ? TOLERANCIA_PADRAO_DIAS : corpo.toleranciaAtrasoDias());
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
