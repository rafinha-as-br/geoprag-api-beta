package br.com.geoprag.api.sync.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.repository.UsuarioRepository;
import br.com.geoprag.api.ponto.domain.DataAgendada;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.domain.StatusDataAgendada;
import br.com.geoprag.api.ponto.repository.DataAgendadaRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;
import br.com.geoprag.api.produto.domain.Produto;
import br.com.geoprag.api.produto.repository.ProdutoRepository;
import br.com.geoprag.api.sync.domain.Aplicacao;
import br.com.geoprag.api.sync.domain.MotivoConflito;
import br.com.geoprag.api.sync.domain.StatusAplicacao;
import br.com.geoprag.api.sync.domain.Subponto;
import br.com.geoprag.api.sync.dto.CodigoRejeicao;
import br.com.geoprag.api.sync.dto.ItemDeRegistro;
import br.com.geoprag.api.sync.dto.ResultadoDoItem;
import br.com.geoprag.api.sync.repository.AplicacaoRepository;
import br.com.geoprag.api.sync.repository.SubpontoRepository;

/**
 * Registro idempotente de um item (subponto) enviado pelo app.
 *
 * <ul>
 *   <li><b>Idempotente</b>: reenviar o mesmo {@code id} devolve {@code DUPLICADO} e não altera nada.</li>
 *   <li><b>Rejeita só o inválido de fato</b>: dados inválidos ou inconsistentes, ponto ou produto inexistente
 *       (ou de outro tenant) e aplicador do token que não é aplicador ativo do tenant. Item rejeitado não grava nada.</li>
 *   <li><b>Conflito é aceito e marcado</b> com um motivo (ponto reatribuído, desativado, inativo, data já atendida
 *       ou cancelada); o trabalho de campo nunca é descartado. O resultado ao app não revela o conflito.</li>
 *   <li>O {@code recebido_em} é do servidor, o aplicador vem do token e o tenant também.</li>
 *   <li>Ao atingir a quantidade de subpontos do ponto, a aplicação é concluída e a data agendada recebe baixa.</li>
 * </ul>
 *
 * <p>Cada chamada é uma transação própria do chamador (o lote da GEOPRAG-201 processa item a item). Itens rejeitados
 * retornam sem lançar exceção e sem gravar, então não deixam a transação marcada para rollback.
 */
@Service
public class RegistroAplicacaoService {

    private static final String MOTIVO_DADOS_INVALIDOS = "Os dados do registro são inválidos ou inconsistentes.";
    private static final String MOTIVO_PONTO_INEXISTENTE = "O ponto informado não existe para o tenant.";
    private static final String MOTIVO_PRODUTO_INEXISTENTE = "O produto informado não existe para o tenant.";

    /** Formato canônico (8-4-4-4-12): o {@code UUID.fromString} aceita variações como "1-1-1-1-1". */
    private static final Pattern UUID_CANONICO =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final int ESCALA_COORDENADA = 6;
    private static final BigDecimal LATITUDE_MAXIMA = BigDecimal.valueOf(90);
    private static final BigDecimal LONGITUDE_MAXIMA = BigDecimal.valueOf(180);

    private final SubpontoRepository subpontos;
    private final AplicacaoRepository aplicacoes;
    private final PontoRepository pontos;
    private final ProdutoRepository produtos;
    private final DataAgendadaRepository datas;
    private final UsuarioRepository usuarios;

    public RegistroAplicacaoService(SubpontoRepository subpontos, AplicacaoRepository aplicacoes,
            PontoRepository pontos, ProdutoRepository produtos, DataAgendadaRepository datas,
            UsuarioRepository usuarios) {
        this.subpontos = subpontos;
        this.aplicacoes = aplicacoes;
        this.pontos = pontos;
        this.produtos = produtos;
        this.datas = datas;
        this.usuarios = usuarios;
    }

    /**
     * @param tenantId   tenant do token
     * @param aplicadorId usuário do token (o aplicador que está registrando)
     */
    @Transactional
    public ResultadoDoItem registrar(String tenantId, UUID aplicadorId, ItemDeRegistro item) {
        if (item == null) {
            return ResultadoDoItem.rejeitado(null, CodigoRejeicao.DADOS_INVALIDOS, MOTIVO_DADOS_INVALIDOS);
        }
        String idTexto = item.id();
        Optional<UUID> id = uuid(item.id());
        Optional<UUID> aplicacaoId = uuid(item.aplicacaoId());
        Optional<UUID> pontoId = uuid(item.pontoId());
        Optional<UUID> dataId = uuid(item.dataAgendadaId());
        Optional<UUID> produtoId = uuid(item.produtoId());
        if (id.isEmpty() || aplicacaoId.isEmpty() || pontoId.isEmpty() || dataId.isEmpty() || produtoId.isEmpty()
                || !coordenadasValidas(item) || item.realizadoEm() == null) {
            return ResultadoDoItem.rejeitado(idTexto, CodigoRejeicao.DADOS_INVALIDOS, MOTIVO_DADOS_INVALIDOS);
        }

        // Idempotência: o mesmo id no mesmo tenant já foi registrado. Em outro tenant o id colide com a chave
        // primária, então não pode ser criado (e também não revela nada além de "inválido").
        if (subpontos.findByTenantIdAndId(tenantId, id.get()).isPresent()) {
            return ResultadoDoItem.duplicado(idTexto);
        }
        if (subpontos.existsById(id.get())) {
            return ResultadoDoItem.rejeitado(idTexto, CodigoRejeicao.DADOS_INVALIDOS, MOTIVO_DADOS_INVALIDOS);
        }

        Optional<Ponto> ponto = pontos.findByTenantIdAndId(tenantId, pontoId.get());
        if (ponto.isEmpty()) {
            return ResultadoDoItem.rejeitado(idTexto, CodigoRejeicao.PONTO_INEXISTENTE, MOTIVO_PONTO_INEXISTENTE);
        }
        Optional<Produto> produto = produtos.findByTenantIdAndId(tenantId, produtoId.get());
        if (produto.isEmpty()) {
            return ResultadoDoItem.rejeitado(idTexto, CodigoRejeicao.PRODUTO_INEXISTENTE,
                    MOTIVO_PRODUTO_INEXISTENTE);
        }
        Optional<Usuario> aplicador = usuarios.findByIdAndTenantId(aplicadorId, tenantId)
                .filter(u -> u.estaAtivo() && u.getPerfil() == Perfil.APLICADOR);
        Optional<DataAgendada> data = datas.findByTenantIdAndId(tenantId, dataId.get())
                .filter(d -> d.getAgendamento().getPonto().getId().equals(pontoId.get()));
        if (aplicador.isEmpty() || data.isEmpty()) {
            return ResultadoDoItem.rejeitado(idTexto, CodigoRejeicao.DADOS_INVALIDOS, MOTIVO_DADOS_INVALIDOS);
        }

        Aplicacao aplicacao = aplicacoes.findByTenantIdAndId(tenantId, aplicacaoId.get()).orElse(null);
        if (aplicacao == null && aplicacoes.existsById(aplicacaoId.get())) {
            // O UUID do app é a chave primária global: já usado em outro tenant, não pode ser criado aqui.
            return ResultadoDoItem.rejeitado(idTexto, CodigoRejeicao.DADOS_INVALIDOS, MOTIVO_DADOS_INVALIDOS);
        }
        if (aplicacao != null && !mesmaAplicacao(aplicacao, ponto.get(), data.get(), produto.get(), aplicadorId)) {
            return ResultadoDoItem.rejeitado(idTexto, CodigoRejeicao.DADOS_INVALIDOS, MOTIVO_DADOS_INVALIDOS);
        }
        if (aplicacao == null) {
            aplicacao = aplicacoes.save(new Aplicacao(aplicacaoId.get(), ponto.get(), data.get(), produto.get(),
                    aplicador.get()));
        }

        boolean outraAplicacaoNaData = aplicacoes.existsByTenantIdAndDataAgendadaIdAndIdNot(tenantId, dataId.get(),
                aplicacaoId.get());
        MotivoConflito motivo = detectarConflito(ponto.get(), data.get(), aplicadorId, outraAplicacaoNaData);
        subpontos.save(new Subponto(id.get(), aplicacao, item.latitude().setScale(ESCALA_COORDENADA, RoundingMode.HALF_UP),
                item.longitude().setScale(ESCALA_COORDENADA, RoundingMode.HALF_UP),
                item.realizadoEm().toInstant(), motivo));

        concluirSeCompleta(tenantId, aplicacao, ponto.get(), data.get(), outraAplicacaoNaData);
        return ResultadoDoItem.criado(idTexto);
    }

    /** Prioridade na ordem do {@link MotivoConflito}: vale o primeiro que se aplica. */
    private static MotivoConflito detectarConflito(Ponto ponto, DataAgendada data, UUID aplicadorId,
            boolean outraAplicacaoNaData) {
        if (ponto.getAplicador() == null || !ponto.getAplicador().getId().equals(aplicadorId)) {
            return MotivoConflito.PONTO_REATRIBUIDO;
        }
        if (ponto.getEstado() == EstadoPonto.DESATIVADO) {
            return MotivoConflito.PONTO_DESATIVADO;
        }
        if (ponto.getEstado() == EstadoPonto.INATIVA) {
            return MotivoConflito.PONTO_INATIVO;
        }
        if (outraAplicacaoNaData) {
            return MotivoConflito.DATA_JA_ATENDIDA;
        }
        if (data.getStatus() == StatusDataAgendada.CANCELADA) {
            return MotivoConflito.DATA_CANCELADA;
        }
        return null;
    }

    /**
     * Conclui a aplicação ao atingir a quantidade de subpontos do ponto e dá baixa na data agendada, desde que
     * nenhuma outra aplicação dispute a mesma data (o servidor é a autoridade sobre a recorrência).
     */
    private void concluirSeCompleta(String tenantId, Aplicacao aplicacao, Ponto ponto, DataAgendada data,
            boolean outraAplicacaoNaData) {
        if (aplicacao.getStatus() == StatusAplicacao.CONCLUIDA) {
            return;
        }
        long registrados = subpontos.countByTenantIdAndAplicacaoId(tenantId, aplicacao.getId());
        if (registrados >= ponto.getQuantidadeDeSubpontos()) {
            aplicacao.concluir(Instant.now());
            if (!outraAplicacaoNaData) {
                data.baixar();
            }
        }
    }

    private static boolean mesmaAplicacao(Aplicacao aplicacao, Ponto ponto, DataAgendada data, Produto produto,
            UUID aplicadorId) {
        return aplicacao.getPonto().getId().equals(ponto.getId())
                && aplicacao.getDataAgendada().getId().equals(data.getId())
                && aplicacao.getProduto().getId().equals(produto.getId())
                && aplicacao.getAplicador().getId().equals(aplicadorId);
    }

    private static boolean coordenadasValidas(ItemDeRegistro item) {
        return item.latitude() != null && item.longitude() != null
                && item.latitude().abs().compareTo(LATITUDE_MAXIMA) <= 0
                && item.longitude().abs().compareTo(LONGITUDE_MAXIMA) <= 0;
    }

    private static Optional<UUID> uuid(String texto) {
        if (texto == null || !UUID_CANONICO.matcher(texto.trim()).matches()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(texto.trim()));
    }
}
