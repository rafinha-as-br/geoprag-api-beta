package br.com.geoprag.api.ponto.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.common.error.ApiException;
import br.com.geoprag.api.ponto.config.PontoProperties;
import br.com.geoprag.api.ponto.domain.AcaoDePonto;
import br.com.geoprag.api.ponto.domain.Agendamento;
import br.com.geoprag.api.ponto.domain.DataAgendada;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.domain.StatusDataAgendada;
import br.com.geoprag.api.ponto.dto.PontoResponse;
import br.com.geoprag.api.ponto.repository.AgendamentoRepository;
import br.com.geoprag.api.ponto.repository.DataAgendadaRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;

/**
 * Transições de estado do ponto feitas pelo administrador (atribuir/remover aplicador, ativar, desativar, reativar,
 * cancelar e remover o alerta de atraso). O servidor é a autoridade sobre o estado: a matriz está em {@link Ponto}; aqui ficam o tenant, o
 * bloqueio do ponto ({@code SELECT … FOR UPDATE}, para que duas mutações do mesmo ponto não se atropelem), a
 * validação das entradas, o agendamento e a auditoria. Cada método é uma transação: uma recusa (409/400) ou uma
 * falha ao gravar a auditoria não deixa nenhuma alteração. Só mudanças efetivas geram evento.
 */
@Service
public class TransicaoDePontoService {

    private static final int ANO_MAXIMO = 9999;

    private final PontoRepository pontos;
    private final AplicadorDoTenant aplicadores;
    private final AgendamentoRepository agendamentos;
    private final DataAgendadaRepository datas;
    private final PontoService leitura;
    private final AuditoriaDePontoService auditoria;
    private final Clock relogio;
    private final PontoProperties propriedades;

    public TransicaoDePontoService(PontoRepository pontos, AplicadorDoTenant aplicadores,
            AgendamentoRepository agendamentos, DataAgendadaRepository datas, PontoService leitura,
            AuditoriaDePontoService auditoria, Clock relogio, PontoProperties propriedades) {
        this.pontos = pontos;
        this.aplicadores = aplicadores;
        this.agendamentos = agendamentos;
        this.datas = datas;
        this.leitura = leitura;
        this.auditoria = auditoria;
        this.relogio = relogio;
        this.propriedades = propriedades;
    }

    @Transactional
    public PontoResponse atribuirAplicador(String tenantId, UUID atorId, String pontoId, String aplicadorId) {
        Ponto ponto = carregar(tenantId, pontoId);
        EstadoPonto estadoAntes = ponto.getEstado();
        UUID aplicadorAntes = idDoAplicador(ponto);
        Usuario novo = aplicadores.resolver(tenantId, aplicadorId);
        ponto.atribuirAplicador(novo);
        if (!Objects.equals(aplicadorAntes, novo.getId())) {
            auditoria.registrar(tenantId, ponto, atorId, AcaoDePonto.ATRIBUICAO_APLICADOR, estadoAntes,
                    aplicadorAntes, null);
        }
        return leitura.detalhar(tenantId, ponto);
    }

    @Transactional
    public PontoResponse removerAplicador(String tenantId, UUID atorId, String pontoId) {
        Ponto ponto = carregar(tenantId, pontoId);
        EstadoPonto estadoAntes = ponto.getEstado();
        UUID aplicadorAntes = idDoAplicador(ponto);
        ponto.removerAplicador();
        auditoria.registrar(tenantId, ponto, atorId, AcaoDePonto.REMOCAO_APLICADOR, estadoAntes, aplicadorAntes,
                null);
        return leitura.detalhar(tenantId, ponto);
    }

    @Transactional
    public PontoResponse ativar(String tenantId, UUID atorId, String pontoId, LocalDate dataInicio,
            int intervaloDias, int quantidadeRecorrencias) {
        Ponto ponto = carregar(tenantId, pontoId);
        ponto.verificarPodeAtivar();
        validarAgendamento(dataInicio, intervaloDias, quantidadeRecorrencias);
        EstadoPonto estadoAntes = ponto.getEstado();

        // No máximo um agendamento vigente por ponto: o anterior (ponto INATIVA) vira histórico e o que ainda
        // estava pendente nele é cancelado.
        agendamentos.findByTenantIdAndPontoIdAndVigenteTrue(tenantId, ponto.getId()).ifPresent(anterior -> {
            anterior.encerrar();
            datas.findByTenantIdAndAgendamentoIdOrderByDataPrevista(tenantId, anterior.getId())
                    .forEach(DataAgendada::cancelar);
        });
        Agendamento agendamento = agendamentos.save(new Agendamento(ponto.getTenant(), ponto, dataInicio,
                intervaloDias, quantidadeRecorrencias));
        for (int i = 0; i < quantidadeRecorrencias; i++) {
            datas.save(new DataAgendada(ponto.getTenant(), agendamento, dataInicio.plusDays((long) i * intervaloDias)));
        }
        ponto.ativar();
        auditoria.registrar(tenantId, ponto, atorId, AcaoDePonto.ATIVACAO, estadoAntes, idDoAplicador(ponto),
                "dataInicio=" + dataInicio + ", intervaloDias=" + intervaloDias + ", quantidadeRecorrencias="
                        + quantidadeRecorrencias);
        return leitura.detalhar(tenantId, ponto);
    }

    /** Desativa o ponto; as datas pendentes de hoje em diante do agendamento vigente passam a canceladas. */
    @Transactional
    public PontoResponse desativar(String tenantId, UUID atorId, String pontoId) {
        Ponto ponto = carregar(tenantId, pontoId);
        EstadoPonto estadoAntes = ponto.getEstado();
        ponto.desativar();
        int canceladas = agendamentos.findByTenantIdAndPontoIdAndVigenteTrue(tenantId, ponto.getId())
                .map(vigente -> cancelarDatasFuturasPendentes(tenantId, vigente)).orElse(0);
        auditoria.registrar(tenantId, ponto, atorId, AcaoDePonto.DESATIVACAO, estadoAntes, idDoAplicador(ponto),
                "datasCanceladas=" + canceladas);
        return leitura.detalhar(tenantId, ponto);
    }

    /** Reativa o ponto desativado, no estado anterior. As datas já canceladas não são restauradas. */
    @Transactional
    public PontoResponse reativar(String tenantId, UUID atorId, String pontoId) {
        Ponto ponto = carregar(tenantId, pontoId);
        EstadoPonto estadoAntes = ponto.getEstado();
        ponto.reativar();
        auditoria.registrar(tenantId, ponto, atorId, AcaoDePonto.REATIVACAO, estadoAntes, idDoAplicador(ponto), null);
        return leitura.detalhar(tenantId, ponto);
    }

    /** Cancela a aplicação do ponto ativo: encerra o agendamento vigente e cancela as datas futuras pendentes. */
    @Transactional
    public PontoResponse cancelar(String tenantId, UUID atorId, String pontoId) {
        Ponto ponto = carregar(tenantId, pontoId);
        EstadoPonto estadoAntes = ponto.getEstado();
        ponto.cancelar();
        int canceladas = agendamentos.findByTenantIdAndPontoIdAndVigenteTrue(tenantId, ponto.getId()).map(vigente -> {
            int total = cancelarDatasFuturasPendentes(tenantId, vigente);
            vigente.encerrar();
            return total;
        }).orElse(0);
        auditoria.registrar(tenantId, ponto, atorId, AcaoDePonto.CANCELAMENTO, estadoAntes, idDoAplicador(ponto),
                "datasCanceladas=" + canceladas);
        return leitura.detalhar(tenantId, ponto);
    }

    /**
     * Remove o alerta de atraso do ponto: só vale para a recorrência atrasada de agora (a pendente mais antiga do
     * agendamento vigente). Fora de atraso, ou com o alerta já removido, é 409. Não muda estado nem aplicador.
     */
    @Transactional
    public PontoResponse removerAlertaDeAtraso(String tenantId, UUID atorId, String pontoId) {
        Ponto ponto = carregar(tenantId, pontoId);
        DataAgendada atrasada = dataAtrasadaComAlerta(tenantId, ponto).orElseThrow(
                () -> ApiException.operacaoNaoPermitida("O ponto não está com a aplicação em atraso."));
        atrasada.removerAlerta(Instant.now(relogio));
        auditoria.registrar(tenantId, ponto, atorId, AcaoDePonto.REMOCAO_ALERTA_ATRASO, ponto.getEstado(),
                idDoAplicador(ponto), "dataAgendadaId=" + atrasada.getId() + ", dataPrevista="
                        + atrasada.getDataPrevista() + ", toleranciaDias=" + ponto.getToleranciaAtrasoDias());
        return leitura.detalhar(tenantId, ponto);
    }

    /** Mesma regra do cliente: em dias corridos, atrasa no dia seguinte ao último dia da tolerância. */
    private Optional<DataAgendada> dataAtrasadaComAlerta(String tenantId, Ponto ponto) {
        if (ponto.getEstado() != EstadoPonto.ATIVA) {
            return Optional.empty();
        }
        LocalDate hoje = LocalDate.now(relogio);
        return agendamentos.findByTenantIdAndPontoIdAndVigenteTrue(tenantId, ponto.getId())
                .flatMap(vigente -> datas.findByTenantIdAndAgendamentoIdOrderByDataPrevista(tenantId, vigente.getId())
                        .stream().filter(d -> d.getStatus() == StatusDataAgendada.PENDENTE).findFirst())
                .filter(d -> d.getAlertaRemovidoEm() == null)
                .filter(d -> ChronoUnit.DAYS.between(d.getDataPrevista(), hoje) > ponto.getToleranciaAtrasoDias());
    }

    private int cancelarDatasFuturasPendentes(String tenantId, Agendamento agendamento) {
        LocalDate hoje = LocalDate.now(relogio);
        return (int) datas.findByTenantIdAndAgendamentoIdOrderByDataPrevista(tenantId, agendamento.getId()).stream()
                .filter(d -> !d.getDataPrevista().isBefore(hoje))
                .filter(DataAgendada::cancelar)
                .count();
    }

    private static UUID idDoAplicador(Ponto ponto) {
        return ponto.getAplicador() == null ? null : ponto.getAplicador().getId();
    }

    private Ponto carregar(String tenantId, String pontoId) {
        UUID id = PontoService.uuid(pontoId).orElseThrow(PontoService::naoEncontrado);
        return pontos.findByTenantIdAndIdParaAtualizar(tenantId, id).orElseThrow(PontoService::naoEncontrado);
    }

    private void validarAgendamento(LocalDate dataInicio, int intervaloDias, int quantidadeRecorrencias) {
        if (dataInicio.isBefore(LocalDate.now(relogio))) {
            throw ApiException.validacao("dataInicio", "A data da primeira aplicação não pode ser passada.");
        }
        if (quantidadeRecorrencias > propriedades.maxRecorrencias()) {
            throw ApiException.validacao("quantidadeRecorrencias",
                    "A quantidade de recorrências não pode passar de " + propriedades.maxRecorrencias() + ".");
        }
        try {
            LocalDate ultima = dataInicio.plusDays((long) (quantidadeRecorrencias - 1) * intervaloDias);
            if (ultima.getYear() > ANO_MAXIMO) {
                throw new DateTimeException("fora do período aceito");
            }
        } catch (DateTimeException e) {
            throw ApiException.validacao("intervaloDias", "O intervalo gera datas fora do período aceito.");
        }
    }
}
