package br.com.geoprag.api.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.Tenant;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.repository.TenantRepository;
import br.com.geoprag.api.auth.repository.UsuarioRepository;
import br.com.geoprag.api.ponto.domain.Bairro;
import br.com.geoprag.api.ponto.domain.DataAgendada;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.repository.BairroRepository;
import br.com.geoprag.api.ponto.repository.DataAgendadaRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;
import br.com.geoprag.api.produto.domain.Produto;
import br.com.geoprag.api.produto.repository.ProdutoRepository;
import br.com.geoprag.api.sync.domain.MotivoConflito;
import br.com.geoprag.api.sync.domain.StatusAplicacao;
import br.com.geoprag.api.sync.domain.Subponto;
import br.com.geoprag.api.sync.dto.CodigoRejeicao;
import br.com.geoprag.api.sync.dto.ItemDeRegistro;
import br.com.geoprag.api.sync.dto.ResultadoDoItem;
import br.com.geoprag.api.sync.dto.StatusItem;
import br.com.geoprag.api.sync.repository.AplicacaoRepository;
import br.com.geoprag.api.sync.repository.SubpontoRepository;
import br.com.geoprag.api.sync.service.RegistroAplicacaoService;

/**
 * Registro idempotente de subpontos, com o contexto completo (perfil test: migrations + seed). Usa o ponto PA-003 do
 * seed (ATIVA, aplicador 3, 6 subpontos, data 2026-10-12 pendente) e o PA-004 (aplicador 4).
 */
@TesteDeIntegracao
@Transactional
class RegistroAplicacaoServiceTests {

    private static final String GASPAR = "gaspar-sc";
    private static final String OUTRO = "outro-sc";
    private static final UUID APLICADOR_3 = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID APLICADOR_4 = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID APLICADOR_DESATIVADO = UUID.fromString("00000000-0000-4000-8000-000000000005");
    private static final UUID PA_003 = UUID.fromString("00000000-0000-4000-8000-000000000203");
    private static final UUID PA_004 = UUID.fromString("00000000-0000-4000-8000-000000000204");
    private static final UUID DATA_PA_003 = UUID.fromString("00000000-0000-4000-8000-000000000402");
    private static final UUID DATA_PA_004 = UUID.fromString("00000000-0000-4000-8000-000000000404");
    private static final UUID PRODUTO = UUID.fromString("00000000-0000-4000-8000-000000000501");
    private static final OffsetDateTime REALIZADO_EM = OffsetDateTime.of(2026, 10, 12, 14, 30, 0, 0,
            ZoneOffset.ofHours(-3));

    @Autowired
    private RegistroAplicacaoService service;
    @Autowired
    private SubpontoRepository subpontos;
    @Autowired
    private AplicacaoRepository aplicacoes;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private BairroRepository bairros;
    @Autowired
    private PontoRepository pontos;
    @Autowired
    private DataAgendadaRepository datas;
    @Autowired
    private ProdutoRepository produtos;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private static ItemDeRegistro item(UUID id, UUID aplicacaoId) {
        return new ItemDeRegistro(id.toString(), aplicacaoId.toString(), PA_003.toString(), DATA_PA_003.toString(),
                PRODUTO.toString(), new BigDecimal("-26.931700"), new BigDecimal("-48.958900"), REALIZADO_EM);
    }

    private ResultadoDoItem registrar(UUID aplicador, ItemDeRegistro item) {
        return service.registrar(GASPAR, aplicador, item);
    }

    private int contagem(String tabela) {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    private void sql(String sql, Object... argumentos) {
        entityManager.flush();
        jdbc.update(sql, argumentos);
        entityManager.clear();
    }

    private Subponto subponto(UUID id) {
        entityManager.flush();
        entityManager.clear();
        return subpontos.findByTenantIdAndId(GASPAR, id).orElseThrow();
    }

    // ---- criação e relógio -------------------------------------------------------------------------------

    @Test
    void criaOSubpontoEAAplicacaoComORelogioDoServidor() {
        UUID id = UUID.randomUUID();
        UUID aplicacao = UUID.randomUUID();
        Instant antes = Instant.now();

        ResultadoDoItem resultado = registrar(APLICADOR_3, item(id, aplicacao));

        assertThat(resultado.status()).isEqualTo(StatusItem.CRIADO);
        assertThat(resultado.id()).isEqualTo(id.toString());
        assertThat(resultado.codigo()).isNull();
        Subponto salvo = subponto(id);
        assertThat(salvo.getRealizadoEm()).isEqualTo(REALIZADO_EM.toInstant());
        // recebido_em é do servidor: agora, não a data (do passado) informada pelo aparelho
        assertThat(salvo.getRecebidoEm()).isBetween(antes.minusSeconds(1), Instant.now().plusSeconds(1));
        assertThat(salvo.getRecebidoEm()).isAfter(salvo.getRealizadoEm());
        assertThat(salvo.isConflito()).isFalse();
        assertThat(salvo.getMotivoConflito()).isNull();
        assertThat(salvo.getLatitude()).isEqualByComparingTo("-26.9317");
        assertThat(salvo.getAplicacao().getAplicador().getId()).isEqualTo(APLICADOR_3);
        assertThat(salvo.getAplicacao().getStatus()).isEqualTo(StatusAplicacao.EM_ANDAMENTO);
    }

    @Test
    void oItemDoContratoNaoTemRecebidoEmNemFotoNemLote() {
        assertThat(java.util.Arrays.stream(ItemDeRegistro.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("id", "aplicacaoId", "pontoId", "dataAgendadaId", "produtoId", "latitude",
                        "longitude", "realizadoEm");
    }

    @Test
    void coordenadasSaoArredondadasParaSeisCasas() {
        UUID id = UUID.randomUUID();
        ItemDeRegistro longo = new ItemDeRegistro(id.toString(), UUID.randomUUID().toString(), PA_003.toString(),
                DATA_PA_003.toString(), PRODUTO.toString(), new BigDecimal("-26.93170049"),
                new BigDecimal("-48.95890051"), REALIZADO_EM);

        assertThat(registrar(APLICADOR_3, longo).status()).isEqualTo(StatusItem.CRIADO);

        assertThat(subponto(id).getLatitude()).isEqualByComparingTo("-26.931700");
        assertThat(subponto(id).getLongitude()).isEqualByComparingTo("-48.958901");
    }

    // ---- idempotência ----------------------------------------------------------------------------------------

    @Test
    void reenviarOMesmoIdNaoCriaRegistroNovoENaoAlteraOExistente() {
        UUID id = UUID.randomUUID();
        UUID aplicacao = UUID.randomUUID();
        registrar(APLICADOR_3, item(id, aplicacao));
        Instant recebidoOriginal = subponto(id).getRecebidoEm();
        int antes = contagem("subponto");

        ResultadoDoItem repetido = registrar(APLICADOR_3, item(id, aplicacao));

        assertThat(repetido.status()).isEqualTo(StatusItem.DUPLICADO);
        assertThat(contagem("subponto")).isEqualTo(antes);
        assertThat(subponto(id).getRecebidoEm()).isEqualTo(recebidoOriginal);
    }

    @Test
    void reenvioComOutrosDadosContinuaDuplicadoESemAlterarOOriginal() {
        UUID id = UUID.randomUUID();
        UUID aplicacao = UUID.randomUUID();
        registrar(APLICADOR_3, item(id, aplicacao));
        ItemDeRegistro alterado = new ItemDeRegistro(id.toString(), aplicacao.toString(), PA_003.toString(),
                DATA_PA_003.toString(), PRODUTO.toString(), new BigDecimal("10"), new BigDecimal("20"),
                REALIZADO_EM.plusDays(1));

        assertThat(registrar(APLICADOR_3, alterado).status()).isEqualTo(StatusItem.DUPLICADO);

        Subponto original = subponto(id);
        assertThat(original.getLatitude()).isEqualByComparingTo("-26.9317");
        assertThat(original.getRealizadoEm()).isEqualTo(REALIZADO_EM.toInstant());
    }

    @Test
    void idJaUsadoEmOutroTenantNaoViraDuplicadoNemEAlterado() {
        UUID id = UUID.randomUUID();
        registrar(APLICADOR_3, item(id, UUID.randomUUID()));
        Tenant outro = tenants.saveAndFlush(new Tenant(OUTRO, "Outro (SC)"));
        Usuario aplicadorOutro = usuarios.save(new Usuario(outro, "Aplicador Outro", "ap.outro@example.com",
                "66666666666", Perfil.APLICADOR, passwordEncoder.encode("x")));
        entityManager.flush();
        int antes = contagem("subponto");

        ResultadoDoItem resultado = service.registrar(OUTRO, aplicadorOutro.getId(), item(id, UUID.randomUUID()));

        assertThat(resultado.status()).isEqualTo(StatusItem.REJEITADO);
        assertThat(resultado.codigo()).isEqualTo(CodigoRejeicao.DADOS_INVALIDOS);
        assertThat(contagem("subponto")).isEqualTo(antes);
    }

    // ---- conclusão e baixa -------------------------------------------------------------------------------------

    @Test
    void aoAtingirAQuantidadeDeSubpontosAAplicacaoConcluiEADataRecebeBaixa() {
        UUID aplicacao = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            registrar(APLICADOR_3, item(UUID.randomUUID(), aplicacao));
        }
        assertThat(aplicacoes.findByTenantIdAndId(GASPAR, aplicacao).orElseThrow().getStatus())
                .isEqualTo(StatusAplicacao.EM_ANDAMENTO);
        assertThat(datas.findByTenantIdAndId(GASPAR, DATA_PA_003).orElseThrow().getStatus().name())
                .isEqualTo("PENDENTE");

        registrar(APLICADOR_3, item(UUID.randomUUID(), aplicacao));

        entityManager.flush();
        entityManager.clear();
        var concluida = aplicacoes.findByTenantIdAndId(GASPAR, aplicacao).orElseThrow();
        assertThat(concluida.getStatus()).isEqualTo(StatusAplicacao.CONCLUIDA);
        assertThat(concluida.getConcluidaEm()).isNotNull();
        assertThat(datas.findByTenantIdAndId(GASPAR, DATA_PA_003).orElseThrow().getStatus().name())
                .isEqualTo("CONCLUIDA");
        assertThat(contagem("subponto")).isEqualTo(6);
    }

    // ---- conflito: aceito e marcado ----------------------------------------------------------------------------

    @Test
    void aplicacaoSobrePontoReatribuidoEAceitaEMarcadaComMotivo() {
        UUID id = UUID.randomUUID();

        // o ponto PA-003 é do aplicador 3; o aplicador 4 estava offline com a tarefa antiga
        ResultadoDoItem resultado = registrar(APLICADOR_4, item(id, UUID.randomUUID()));

        assertThat(resultado.status()).isEqualTo(StatusItem.CRIADO);
        Subponto salvo = subponto(id);
        assertThat(salvo.isConflito()).isTrue();
        assertThat(salvo.getMotivoConflito()).isEqualTo(MotivoConflito.PONTO_REATRIBUIDO);
    }

    @Test
    void oResultadoAoAplicadorNaoRevelaOConflito() {
        ResultadoDoItem resultado = registrar(APLICADOR_4, item(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(resultado.status()).isEqualTo(StatusItem.CRIADO);
        assertThat(resultado.codigo()).isNull();
        assertThat(resultado.motivo()).isNull();
        assertThat(java.util.Arrays.stream(ResultadoDoItem.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)).containsExactly("id", "status", "codigo", "motivo");
    }

    @Test
    void pontoSemAplicadorTambemEhReatribuido() {
        sql("update ponto set aplicador_id = null, estado = 'INATIVA' where id = ?", PA_003);
        UUID id = UUID.randomUUID();

        registrar(APLICADOR_3, item(id, UUID.randomUUID()));

        assertThat(subponto(id).getMotivoConflito()).isEqualTo(MotivoConflito.PONTO_REATRIBUIDO);
    }

    @Test
    void pontoDesativadoEhAceitoEMarcado() {
        sql("update ponto set estado = 'DESATIVADO', estado_anterior = 'ATIVA' where id = ?", PA_003);
        UUID id = UUID.randomUUID();

        assertThat(registrar(APLICADOR_3, item(id, UUID.randomUUID())).status()).isEqualTo(StatusItem.CRIADO);

        assertThat(subponto(id).getMotivoConflito()).isEqualTo(MotivoConflito.PONTO_DESATIVADO);
    }

    @Test
    void pontoInativoEhAceitoEMarcado() {
        sql("update ponto set estado = 'INATIVA', estado_anterior = 'ATIVA' where id = ?", PA_003);
        UUID id = UUID.randomUUID();

        registrar(APLICADOR_3, item(id, UUID.randomUUID()));

        assertThat(subponto(id).getMotivoConflito()).isEqualTo(MotivoConflito.PONTO_INATIVO);
    }

    @Test
    void jaExistirAplicacaoParaAMesmaDataMarcaOSegundoRegistroSemDescartar() {
        registrar(APLICADOR_3, item(UUID.randomUUID(), UUID.randomUUID()));
        UUID segundo = UUID.randomUUID();

        ResultadoDoItem resultado = registrar(APLICADOR_3, item(segundo, UUID.randomUUID()));

        assertThat(resultado.status()).isEqualTo(StatusItem.CRIADO);
        assertThat(subponto(segundo).getMotivoConflito()).isEqualTo(MotivoConflito.DATA_JA_ATENDIDA);
        assertThat(contagem("subponto")).isEqualTo(2);
    }

    @Test
    void dataCanceladaEhAceitaEMarcada() {
        sql("update data_agendada set status = 'CANCELADA' where id = ?", DATA_PA_003);
        UUID id = UUID.randomUUID();

        registrar(APLICADOR_3, item(id, UUID.randomUUID()));

        assertThat(subponto(id).getMotivoConflito()).isEqualTo(MotivoConflito.DATA_CANCELADA);
    }

    @Test
    void comMaisDeUmMotivoVaiOPrimeiroDaPrioridade() {
        sql("update ponto set estado = 'DESATIVADO', estado_anterior = 'ATIVA' where id = ?", PA_003);
        UUID id = UUID.randomUUID();

        // aplicador 4 (reatribuído) + ponto desativado: vale PONTO_REATRIBUIDO
        registrar(APLICADOR_4, item(id, UUID.randomUUID()));

        assertThat(subponto(id).getMotivoConflito()).isEqualTo(MotivoConflito.PONTO_REATRIBUIDO);
    }

    @Test
    void aplicacaoConflitanteNaoDaBaixaNaDataDeOutraAplicacao() {
        UUID primeira = UUID.randomUUID();
        registrar(APLICADOR_3, item(UUID.randomUUID(), primeira));
        UUID segunda = UUID.randomUUID();
        for (int i = 0; i < 6; i++) {
            registrar(APLICADOR_3, item(UUID.randomUUID(), segunda));
        }

        entityManager.flush();
        entityManager.clear();
        assertThat(aplicacoes.findByTenantIdAndId(GASPAR, segunda).orElseThrow().getStatus())
                .isEqualTo(StatusAplicacao.CONCLUIDA);
        // a data continua pendente: a primeira aplicação (a disputante original) ainda não terminou
        assertThat(datas.findByTenantIdAndId(GASPAR, DATA_PA_003).orElseThrow().getStatus().name())
                .isEqualTo("PENDENTE");
    }

    @Test
    void marcaDeConflitoFicaConsultavelPorPontoParaOGestor() {
        UUID comConflito = UUID.randomUUID();
        UUID semConflito = UUID.randomUUID();
        registrar(APLICADOR_3, item(semConflito, UUID.randomUUID()));
        registrar(APLICADOR_4, item(comConflito, UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();

        List<Subponto> doPonto = subpontos.findByTenantIdAndAplicacaoPontoIdOrderByRealizadoEm(GASPAR, PA_003);

        assertThat(doPonto).extracting(Subponto::getId).containsExactlyInAnyOrder(comConflito, semConflito);
        assertThat(doPonto).filteredOn(Subponto::isConflito).extracting(Subponto::getMotivoConflito)
                .containsExactly(MotivoConflito.PONTO_REATRIBUIDO);
        assertThat(subpontos.findByTenantIdAndAplicacaoPontoIdOrderByRealizadoEm(OUTRO, PA_003)).isEmpty();
    }

    // ---- rejeições (só o inválido de fato) -----------------------------------------------------------------------

    private void assertRejeitado(ResultadoDoItem resultado, CodigoRejeicao codigo) {
        assertThat(resultado.status()).isEqualTo(StatusItem.REJEITADO);
        assertThat(resultado.codigo()).isEqualTo(codigo);
        assertThat(resultado.motivo()).isNotBlank();
    }

    @Test
    void pontoInexistenteEhRejeitadoESemGravarNada() {
        int antes = contagem("aplicacao") + contagem("subponto");
        ItemDeRegistro item = new ItemDeRegistro(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), DATA_PA_003.toString(), PRODUTO.toString(), new BigDecimal("1"),
                new BigDecimal("1"), REALIZADO_EM);

        assertRejeitado(registrar(APLICADOR_3, item), CodigoRejeicao.PONTO_INEXISTENTE);

        assertThat(contagem("aplicacao") + contagem("subponto")).isEqualTo(antes);
    }

    @Test
    void pontoDeOutroTenantEhRejeitadoComoInexistente() {
        Tenant outro = tenants.saveAndFlush(new Tenant(OUTRO, "Outro (SC)"));
        Bairro bairro = bairros.save(new Bairro(outro, "Centro"));
        Ponto pontoOutro = pontos.save(new Ponto(outro, "PA-001", "De outro", bairro, "Rua X", new BigDecimal("10"),
                new BigDecimal("5"), 3, null));
        entityManager.flush();
        ItemDeRegistro item = new ItemDeRegistro(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                pontoOutro.getId().toString(), DATA_PA_003.toString(), PRODUTO.toString(), new BigDecimal("1"),
                new BigDecimal("1"), REALIZADO_EM);

        assertRejeitado(registrar(APLICADOR_3, item), CodigoRejeicao.PONTO_INEXISTENTE);
    }

    @Test
    void produtoInexistenteOuDeOutroTenantEhRejeitado() {
        Tenant outro = tenants.saveAndFlush(new Tenant(OUTRO, "Outro (SC)"));
        Produto produtoOutro = produtos.save(new Produto(outro, "Produto de outro"));
        entityManager.flush();

        ItemDeRegistro inexistente = new ItemDeRegistro(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                PA_003.toString(), DATA_PA_003.toString(), UUID.randomUUID().toString(), new BigDecimal("1"),
                new BigDecimal("1"), REALIZADO_EM);
        ItemDeRegistro deOutro = new ItemDeRegistro(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                PA_003.toString(), DATA_PA_003.toString(), produtoOutro.getId().toString(), new BigDecimal("1"),
                new BigDecimal("1"), REALIZADO_EM);

        assertRejeitado(registrar(APLICADOR_3, inexistente), CodigoRejeicao.PRODUTO_INEXISTENTE);
        assertRejeitado(registrar(APLICADOR_3, deOutro), CodigoRejeicao.PRODUTO_INEXISTENTE);
    }

    @Test
    void payloadInvalidoOuInconsistenteEhRejeitadoComoDadosInvalidos() {
        UUID id = UUID.randomUUID();
        String ap = UUID.randomUUID().toString();
        ItemDeRegistro base = item(id, UUID.fromString(ap));
        List<ItemDeRegistro> invalidos = List.of(
                new ItemDeRegistro("não-é-uuid", ap, PA_003.toString(), DATA_PA_003.toString(), PRODUTO.toString(),
                        base.latitude(), base.longitude(), REALIZADO_EM),
                new ItemDeRegistro("1-1-1-1-1", ap, PA_003.toString(), DATA_PA_003.toString(), PRODUTO.toString(),
                        base.latitude(), base.longitude(), REALIZADO_EM),
                new ItemDeRegistro(id.toString(), null, PA_003.toString(), DATA_PA_003.toString(),
                        PRODUTO.toString(), base.latitude(), base.longitude(), REALIZADO_EM),
                new ItemDeRegistro(id.toString(), ap, "x", DATA_PA_003.toString(), PRODUTO.toString(),
                        base.latitude(), base.longitude(), REALIZADO_EM),
                new ItemDeRegistro(id.toString(), ap, PA_003.toString(), DATA_PA_003.toString(), PRODUTO.toString(),
                        new BigDecimal("91"), base.longitude(), REALIZADO_EM),
                new ItemDeRegistro(id.toString(), ap, PA_003.toString(), DATA_PA_003.toString(), PRODUTO.toString(),
                        base.latitude(), new BigDecimal("-181"), REALIZADO_EM),
                new ItemDeRegistro(id.toString(), ap, PA_003.toString(), DATA_PA_003.toString(), PRODUTO.toString(),
                        null, base.longitude(), REALIZADO_EM),
                new ItemDeRegistro(id.toString(), ap, PA_003.toString(), DATA_PA_003.toString(), PRODUTO.toString(),
                        base.latitude(), base.longitude(), null),
                // data agendada inexistente
                new ItemDeRegistro(id.toString(), ap, PA_003.toString(), UUID.randomUUID().toString(),
                        PRODUTO.toString(), base.latitude(), base.longitude(), REALIZADO_EM),
                // data agendada de outro ponto
                new ItemDeRegistro(id.toString(), ap, PA_003.toString(), DATA_PA_004.toString(), PRODUTO.toString(),
                        base.latitude(), base.longitude(), REALIZADO_EM));

        for (ItemDeRegistro invalido : invalidos) {
            assertRejeitado(registrar(APLICADOR_3, invalido), CodigoRejeicao.DADOS_INVALIDOS);
        }
        assertRejeitado(registrar(APLICADOR_3, null), CodigoRejeicao.DADOS_INVALIDOS);
        assertThat(contagem("subponto")).isZero();
    }

    @Test
    void mesmaAplicacaoComOutroPontoOuAplicadorEhInconsistente() {
        UUID aplicacao = UUID.randomUUID();
        registrar(APLICADOR_3, item(UUID.randomUUID(), aplicacao));

        ItemDeRegistro outroPonto = new ItemDeRegistro(UUID.randomUUID().toString(), aplicacao.toString(),
                PA_004.toString(), DATA_PA_004.toString(), PRODUTO.toString(), new BigDecimal("1"),
                new BigDecimal("1"), REALIZADO_EM);

        assertRejeitado(registrar(APLICADOR_3, outroPonto), CodigoRejeicao.DADOS_INVALIDOS);
        assertRejeitado(registrar(APLICADOR_4, item(UUID.randomUUID(), aplicacao)), CodigoRejeicao.DADOS_INVALIDOS);
        assertThat(contagem("subponto")).isEqualTo(1);
    }

    @Test
    void aplicadorDoTokenDesativadoInexistenteOuDeOutroTenantEhRejeitado() {
        assertRejeitado(registrar(APLICADOR_DESATIVADO, item(UUID.randomUUID(), UUID.randomUUID())),
                CodigoRejeicao.DADOS_INVALIDOS);
        assertRejeitado(registrar(UUID.randomUUID(), item(UUID.randomUUID(), UUID.randomUUID())),
                CodigoRejeicao.DADOS_INVALIDOS);
        // administrador não registra aplicação
        assertRejeitado(registrar(UUID.fromString("00000000-0000-4000-8000-000000000001"),
                item(UUID.randomUUID(), UUID.randomUUID())), CodigoRejeicao.DADOS_INVALIDOS);
        // aplicador de outro tenant, mesmo com tenant do token diferente
        Tenant outro = tenants.saveAndFlush(new Tenant(OUTRO, "Outro (SC)"));
        Usuario aplicadorOutro = usuarios.save(new Usuario(outro, "Aplicador Outro", "ap.outro2@example.com",
                "77777777777", Perfil.APLICADOR, passwordEncoder.encode("x")));
        entityManager.flush();
        assertRejeitado(registrar(aplicadorOutro.getId(), item(UUID.randomUUID(), UUID.randomUUID())),
                CodigoRejeicao.DADOS_INVALIDOS);
        assertThat(contagem("subponto")).isZero();
    }

    // ---- isolamento por tenant ---------------------------------------------------------------------------------

    @Test
    void registrosDeUmTenantNaoApareceParaOutro() {
        UUID id = UUID.randomUUID();
        registrar(APLICADOR_3, item(id, UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();

        assertThat(subpontos.findByTenantIdAndId(GASPAR, id)).isPresent();
        assertThat(subpontos.findByTenantIdAndId(OUTRO, id)).isEmpty();
    }

    // ---- append-only ---------------------------------------------------------------------------------------------

    @Test
    void entidadesDeCampoNaoTemSettersNemOperacaoDeExclusao() {
        for (Class<?> tipo : List.of(Subponto.class, br.com.geoprag.api.sync.domain.Aplicacao.class)) {
            assertThat(java.util.Arrays.stream(tipo.getMethods()).map(java.lang.reflect.Method::getName))
                    .as(tipo.getSimpleName()).noneMatch(nome -> nome.startsWith("set"));
        }
        for (Class<?> repositorio : List.of(SubpontoRepository.class, AplicacaoRepository.class)) {
            assertThat(java.util.Arrays.stream(repositorio.getDeclaredMethods())
                    .map(java.lang.reflect.Method::getName))
                    .as(repositorio.getSimpleName()).noneMatch(nome -> nome.startsWith("delete")
                            || nome.startsWith("remove"));
        }
    }

    @Test
    void toStringNaoExpoeCoordenadas() {
        UUID id = UUID.randomUUID();
        registrar(APLICADOR_3, item(id, UUID.randomUUID()));

        assertThat(subponto(id).toString()).doesNotContain("26.9317").doesNotContain("48.9589");
    }
}
