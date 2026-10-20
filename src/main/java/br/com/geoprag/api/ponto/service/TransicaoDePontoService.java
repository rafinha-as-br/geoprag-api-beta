package br.com.geoprag.api.ponto.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.StatusUsuario;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.repository.UsuarioRepository;
import br.com.geoprag.api.common.error.ApiException;
import br.com.geoprag.api.ponto.config.PontoProperties;
import br.com.geoprag.api.ponto.domain.Agendamento;
import br.com.geoprag.api.ponto.domain.DataAgendada;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.dto.PontoResponse;
import br.com.geoprag.api.ponto.repository.AgendamentoRepository;
import br.com.geoprag.api.ponto.repository.DataAgendadaRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;

/**
 * Transições de estado do ponto feitas pelo administrador (atribuir/remover aplicador e ativar). O servidor é a
 * autoridade sobre o estado: a matriz está em {@link Ponto}; aqui ficam o tenant, o bloqueio do ponto
 * ({@code SELECT … FOR UPDATE}, para que duas mutações do mesmo ponto não se atropelem), a validação das entradas
 * e o agendamento. Cada método é uma transação: uma recusa (409/400) não deixa nenhuma alteração.
 */
@Service
public class TransicaoDePontoService {

    private static final int ANO_MAXIMO = 9999;

    private final PontoRepository pontos;
    private final UsuarioRepository usuarios;
    private final AgendamentoRepository agendamentos;
    private final DataAgendadaRepository datas;
    private final PontoService leitura;
    private final Clock relogio;
    private final PontoProperties propriedades;

    public TransicaoDePontoService(PontoRepository pontos, UsuarioRepository usuarios,
            AgendamentoRepository agendamentos, DataAgendadaRepository datas, PontoService leitura, Clock relogio,
            PontoProperties propriedades) {
        this.pontos = pontos;
        this.usuarios = usuarios;
        this.agendamentos = agendamentos;
        this.datas = datas;
        this.leitura = leitura;
        this.relogio = relogio;
        this.propriedades = propriedades;
    }

    @Transactional
    public PontoResponse atribuirAplicador(String tenantId, String pontoId, String aplicadorId) {
        Ponto ponto = carregar(tenantId, pontoId);
        ponto.atribuirAplicador(aplicadorDoTenant(tenantId, aplicadorId));
        return leitura.detalhar(tenantId, ponto);
    }

    @Transactional
    public PontoResponse removerAplicador(String tenantId, String pontoId) {
        Ponto ponto = carregar(tenantId, pontoId);
        ponto.removerAplicador();
        return leitura.detalhar(tenantId, ponto);
    }

    @Transactional
    public PontoResponse ativar(String tenantId, String pontoId, LocalDate dataInicio, int intervaloDias,
            int quantidadeRecorrencias) {
        Ponto ponto = carregar(tenantId, pontoId);
        ponto.verificarPodeAtivar();
        validarAgendamento(dataInicio, intervaloDias, quantidadeRecorrencias);

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
        return leitura.detalhar(tenantId, ponto);
    }

    private Ponto carregar(String tenantId, String pontoId) {
        UUID id = PontoService.uuid(pontoId).orElseThrow(PontoService::naoEncontrado);
        return pontos.findByTenantIdAndIdParaAtualizar(tenantId, id).orElseThrow(PontoService::naoEncontrado);
    }

    /**
     * Inexistente, de outro tenant ou que não é aplicador: {@code 400} (sem revelar outro tenant); aplicador
     * desativado do próprio tenant: {@code 409}.
     */
    private Usuario aplicadorDoTenant(String tenantId, String aplicadorId) {
        Usuario aplicador = PontoService.uuid(aplicadorId)
                .flatMap(id -> usuarios.findByIdAndTenantId(id, tenantId))
                .filter(u -> u.getPerfil() == Perfil.APLICADOR)
                .orElseThrow(() -> ApiException.validacao("aplicadorId", "Informe um aplicador válido."));
        if (aplicador.getStatus() == StatusUsuario.DESATIVADO) {
            throw ApiException.operacaoNaoPermitida("O aplicador está desativado e não pode ser atribuído a um ponto.");
        }
        return aplicador;
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
