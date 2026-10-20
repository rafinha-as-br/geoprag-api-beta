package br.com.geoprag.api.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;
import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.Tenant;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.repository.TenantRepository;
import br.com.geoprag.api.auth.repository.UsuarioRepository;
import br.com.geoprag.api.ponto.domain.Agendamento;
import br.com.geoprag.api.ponto.domain.Bairro;
import br.com.geoprag.api.ponto.domain.DataAgendada;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.repository.AgendamentoRepository;
import br.com.geoprag.api.ponto.repository.BairroRepository;
import br.com.geoprag.api.ponto.repository.DataAgendadaRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;
import br.com.geoprag.api.produto.domain.Produto;
import br.com.geoprag.api.produto.repository.ProdutoRepository;
import br.com.geoprag.api.sync.dto.ItemDeRegistro;
import br.com.geoprag.api.sync.dto.ResultadoDoItem;
import br.com.geoprag.api.sync.dto.StatusItem;
import br.com.geoprag.api.sync.service.RegistroAplicacaoService;

/**
 * {@code POST /api/v1/sync/aplicacoes} contra um <b>PostgreSQL real</b> (Testcontainers), sem transação de teste:
 * cada item é gravado e confirmado de verdade, como em produção. Precisa de Docker; sem Docker a classe é ignorada
 * (o CI roda com Docker). O lote máximo é reduzido para 5 só para exercitar o {@code 413}.
 */
@Testcontainers(disabledWithoutDocker = true)
@TesteDeIntegracao
@TestPropertySource(properties = "app.sync.max-itens-por-lote=5")
class EnvioEmLotePostgresTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final String DATA_PA_003 = "00000000-0000-4000-8000-000000000402";
    private static final String PRODUTO = "00000000-0000-4000-8000-000000000501";
    private static final String REALIZADO_EM = "2026-10-12T14:30:00-03:00";
    private static final String URL = "/api/v1/sync/aplicacoes";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RegistroAplicacaoService registro;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private BairroRepository bairros;
    @Autowired
    private PontoRepository pontos;
    @Autowired
    private AgendamentoRepository agendamentos;
    @Autowired
    private DataAgendadaRepository datas;
    @Autowired
    private ProdutoRepository produtos;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeAll
    static void comecaDoSeed() {
        // O contexto aplica as migrations e o seed no contêiner; nada a fazer aqui além de garantir que subiu.
        assertThat(POSTGRES.isRunning()).isTrue();
    }

    @AfterEach
    void limpa() {
        jdbc.execute("alter table subponto drop constraint if exists ck_teste_latitude");
        jdbc.update("delete from subponto");
        jdbc.update("delete from aplicacao");
        jdbc.update("update data_agendada set status = 'PENDENTE' where id in (?, ?, ?, ?, ?)",
                UUID.fromString(DATA_PA_003), UUID.fromString("00000000-0000-4000-8000-000000000403"),
                UUID.fromString("00000000-0000-4000-8000-000000000404"),
                UUID.fromString("00000000-0000-4000-8000-000000000405"),
                UUID.fromString("00000000-0000-4000-8000-000000000406"));
    }

    // ---- montagem do pedido ------------------------------------------------------------------------------------

    private ObjectNode item(String id, String aplicacaoId) {
        ObjectNode item = json.createObjectNode();
        item.put("id", id);
        item.put("aplicacaoId", aplicacaoId);
        item.put("pontoId", PA_003);
        item.put("dataAgendadaId", DATA_PA_003);
        item.put("produtoId", PRODUTO);
        item.put("latitude", -26.9317);
        item.put("longitude", -48.9589);
        item.put("realizadoEm", REALIZADO_EM);
        return item;
    }

    private ObjectNode itemNovo(String aplicacaoId) {
        return item(UUID.randomUUID().toString(), aplicacaoId);
    }

    private String lote(ObjectNode... itens) {
        ObjectNode corpo = json.createObjectNode();
        corpo.put("dispositivoId", "aparelho-0001");
        ArrayNode array = corpo.putArray("itens");
        for (ObjectNode item : itens) {
            array.add(item);
        }
        return corpo.toString();
    }

    private ResultActions enviar(String authorization, String corpo) throws Exception {
        var requisicao = post(URL).contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (authorization != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(requisicao);
    }

    private ResultActions enviarComoAplicador(ObjectNode... itens) throws Exception {
        return enviar(tokens.bearerAplicador(), lote(itens));
    }

    private int contagem(String tabela) {
        return jdbc.queryForObject("select count(*) from " + tabela, Integer.class);
    }

    // ---- lote misto, ordem e idempotência ----------------------------------------------------------------------

    @Test
    void loteMistoDevolveUmResultadoPorItemNaMesmaOrdem() throws Exception {
        String aplicacao = UUID.randomUUID().toString();
        ObjectNode jaEnviado = itemNovo(aplicacao);
        enviarComoAplicador(jaEnviado).andExpect(status().isOk());

        ObjectNode novo1 = itemNovo(aplicacao);
        ObjectNode pontoInexistente = itemNovo(aplicacao).put("pontoId", UUID.randomUUID().toString());
        ObjectNode semData = itemNovo(aplicacao);
        semData.putNull("realizadoEm");
        ObjectNode novo2 = itemNovo(aplicacao);

        enviarComoAplicador(novo1, jaEnviado, pontoInexistente, semData, novo2)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.recebidoEm").isNotEmpty())
                .andExpect(jsonPath("$.resultados", hasSize(5)))
                .andExpect(jsonPath("$.resultados[*].id", contains(novo1.get("id").asText(),
                        jaEnviado.get("id").asText(), pontoInexistente.get("id").asText(),
                        semData.get("id").asText(), novo2.get("id").asText())))
                .andExpect(jsonPath("$.resultados[*].status",
                        contains("CRIADO", "DUPLICADO", "REJEITADO", "REJEITADO", "CRIADO")))
                .andExpect(jsonPath("$.resultados[2].codigo").value("PONTO_INEXISTENTE"))
                .andExpect(jsonPath("$.resultados[2].motivo").value("O ponto informado não existe para o tenant."))
                .andExpect(jsonPath("$.resultados[3].codigo").value("DADOS_INVALIDOS"))
                // criado e duplicado não trazem codigo nem motivo
                .andExpect(jsonPath("$.resultados[0].codigo").doesNotExist())
                .andExpect(jsonPath("$.resultados[1].motivo").doesNotExist());

        // o item inválido não derrubou os demais: 3 subpontos gravados (o já enviado e os dois novos)
        assertThat(contagem("subponto")).isEqualTo(3);
    }

    @Test
    void repetirOLoteInteiroDevolveTudoDuplicadoENaoAlteraOBanco() throws Exception {
        String aplicacao = UUID.randomUUID().toString();
        String corpo = lote(itemNovo(aplicacao), itemNovo(aplicacao), itemNovo(aplicacao));
        enviar(tokens.bearerAplicador(), corpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[*].status", contains("CRIADO", "CRIADO", "CRIADO")));
        List<String> antes = jdbc.queryForList(
                "select id || '|' || recebido_em from subponto order by id", String.class);

        enviar(tokens.bearerAplicador(), corpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[*].status", contains("DUPLICADO", "DUPLICADO", "DUPLICADO")));

        assertThat(jdbc.queryForList("select id || '|' || recebido_em from subponto order by id", String.class))
                .isEqualTo(antes);
        assertThat(contagem("aplicacao")).isEqualTo(1);
    }

    @Test
    void omesmoItemRepetidoNoMesmoLoteViraDuplicado() throws Exception {
        ObjectNode item = itemNovo(UUID.randomUUID().toString());

        enviarComoAplicador(item, item).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[*].status", contains("CRIADO", "DUPLICADO")));

        assertThat(contagem("subponto")).isEqualTo(1);
    }

    @Test
    void registroOnlineEOMesmoEnvioComUmItem() throws Exception {
        enviarComoAplicador(itemNovo(UUID.randomUUID().toString())).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados", hasSize(1)))
                .andExpect(jsonPath("$.resultados[0].status").value("CRIADO"));
    }

    // ---- transação por item -------------------------------------------------------------------------------------

    @Test
    void falhaInesperadaEmUmItemNaoDesfazOQueJaFoiGravadoEOReenvioCompleta() throws Exception {
        // Um check temporário faz o banco recusar a latitude -1 depois de o item passar pela validação do serviço.
        jdbc.execute("alter table subponto add constraint ck_teste_latitude check (latitude <> -1)");
        String aplicacao = UUID.randomUUID().toString();
        ObjectNode antes = itemNovo(aplicacao);
        ObjectNode quebra = itemNovo(aplicacao).put("latitude", -1);
        ObjectNode depois = itemNovo(aplicacao);

        enviarComoAplicador(antes, quebra, depois).andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ERRO_INTERNO"))
                .andExpect(content().string(not(containsString("ck_teste_latitude"))));

        // o item anterior foi confirmado na própria transação; o que quebrou e o seguinte não foram gravados
        assertThat(jdbc.queryForList("select id from subponto", String.class))
                .containsExactly(antes.get("id").asText());

        // o app reenvia o lote (sem o item inválido): o anterior volta como DUPLICADO e o seguinte é criado
        jdbc.execute("alter table subponto drop constraint ck_teste_latitude");
        enviarComoAplicador(antes, depois).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[*].status", contains("DUPLICADO", "CRIADO")));
        assertThat(contagem("subponto")).isEqualTo(2);
    }

    // ---- limite e envelope --------------------------------------------------------------------------------------

    @Test
    void loteAcimaDoLimiteRetorna413SemGravarNada() throws Exception {
        String aplicacao = UUID.randomUUID().toString();
        List<ObjectNode> itens = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            itens.add(itemNovo(aplicacao));
        }

        enviarComoAplicador(itens.toArray(ObjectNode[]::new)).andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("LOTE_MUITO_GRANDE"))
                .andExpect(jsonPath("$.status").value(413));
        assertThat(contagem("subponto")).isZero();

        // exatamente no limite é aceito
        enviarComoAplicador(itens.subList(0, 5).toArray(ObjectNode[]::new)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados", hasSize(5)));
    }

    @Test
    void envelopeInvalidoRetornaValidacaoInvalida() throws Exception {
        String ap = tokens.bearerAplicador();

        enviar(ap, "{\"dispositivoId\":\"a\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("itens"));
        enviar(ap, "{\"dispositivoId\":\"a\",\"itens\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        enviar(ap, "{\"itens\":[{}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("dispositivoId"));
        enviar(ap, "{nao é json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUISICAO_INVALIDA"));
        assertThat(contagem("subponto")).isZero();
    }

    @Test
    void itemNuloOuVazioNoLoteEhRejeitadoSemDerrubarOsDemais() throws Exception {
        ObjectNode valido = itemNovo(UUID.randomUUID().toString());
        String corpo = "{\"dispositivoId\":\"a\",\"itens\":[null,{}," + valido + "]}";

        enviar(tokens.bearerAplicador(), corpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[*].status", contains("REJEITADO", "REJEITADO", "CRIADO")))
                .andExpect(jsonPath("$.resultados[0].codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.resultados[1].codigo").value("DADOS_INVALIDOS"));
    }

    // ---- segurança e sigilo ----------------------------------------------------------------------------------------

    @Test
    void soOAplicadorEnvia() throws Exception {
        String corpo = lote(itemNovo(UUID.randomUUID().toString()));

        enviar(tokens.bearerAdministrador(), corpo).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        enviar(tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, "gaspar-sc", "SUB_ADMINISTRADOR"), corpo)
                .andExpect(status().isForbidden());
        enviar(null, corpo).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code")
                .value("NAO_AUTENTICADO"));
        assertThat(contagem("subponto")).isZero();
    }

    @Test
    void oAplicadorEOTenantVemDoTokenNuncaDoCorpo() throws Exception {
        // token de um tenant que não é o do ponto: o ponto "não existe" para ele
        String tokenOutroTenant = tokens.bearer(TokensDeTeste.ID_APLICADOR, "outro-sc", "APLICADOR");
        enviar(tokenOutroTenant, lote(itemNovo(UUID.randomUUID().toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].codigo").value("PONTO_INEXISTENTE"));

        // o aplicador 4 envia para um ponto do aplicador 3: aceito e marcado, sem revelar ao app
        String aplicador4 = tokens.bearer("00000000-0000-4000-8000-000000000004", "gaspar-sc", "APLICADOR");
        String corpo = enviar(aplicador4, lote(itemNovo(UUID.randomUUID().toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].status").value("CRIADO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("conflito").doesNotContain("PONTO_REATRIBUIDO");
        assertThat(jdbc.queryForObject("select count(*) from subponto where conflito = true and "
                + "motivo_conflito = 'PONTO_REATRIBUIDO'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from aplicacao where aplicador_id = ?", Integer.class,
                UUID.fromString("00000000-0000-4000-8000-000000000004"))).isEqualTo(1);
    }

    @Test
    void recebidoEmEDoServidorMesmoComRealizadoEmNoPassado() throws Exception {
        enviarComoAplicador(itemNovo(UUID.randomUUID().toString())).andExpect(status().isOk());

        Long segundosEntreOsDoisRelogios = jdbc.queryForObject(
                "select extract(epoch from (recebido_em - realizado_em)) from subponto", Long.class);
        assertThat(segundosEntreOsDoisRelogios).isGreaterThan(24L * 3600);
        Long segundosDesdeOEnvio = jdbc.queryForObject(
                "select extract(epoch from (now() - recebido_em)) from subponto", Long.class);
        assertThat(segundosDesdeOEnvio).isBetween(-5L, 60L);
    }

    @Test
    void aplicacaoIdJaUsadoEmOutroTenantEhRejeitadoSemEstourarOErro500() throws Exception {
        Tenant outro = tenants.saveAndFlush(new Tenant("outro-sc", "Outro (SC)"));
        try {
            Bairro bairro = bairros.save(new Bairro(outro, "Centro"));
            Usuario aplicadorOutro = usuarios.save(new Usuario(outro, "Aplicador Outro", "ap.pg@example.com",
                    "77777777777", Perfil.APLICADOR, passwordEncoder.encode("x")));
            Ponto pontoOutro = pontos.save(new Ponto(outro, "PA-001", "De outro", bairro, "Rua X",
                    new BigDecimal("10"), new BigDecimal("5"), 3, aplicadorOutro));
            Agendamento agendamentoOutro = agendamentos.save(new Agendamento(outro, pontoOutro,
                    LocalDate.of(2026, 12, 1), 7, 1));
            DataAgendada dataOutro = datas.save(new DataAgendada(outro, agendamentoOutro, LocalDate.of(2026, 12, 1)));
            Produto produtoOutro = produtos.save(new Produto(outro, "Produto de outro"));
            UUID aplicacaoCompartilhada = UUID.randomUUID();
            ResultadoDoItem doOutro = registro.registrar("outro-sc", aplicadorOutro.getId(),
                    new ItemDeRegistro(UUID.randomUUID().toString(), aplicacaoCompartilhada.toString(),
                            pontoOutro.getId().toString(), dataOutro.getId().toString(),
                            produtoOutro.getId().toString(), new BigDecimal("1"), new BigDecimal("1"),
                            OffsetDateTime.parse(REALIZADO_EM)));
            assertThat(doOutro.status()).isEqualTo(StatusItem.CRIADO);

            enviarComoAplicador(itemNovo(aplicacaoCompartilhada.toString()), itemNovo(UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resultados[*].status", contains("REJEITADO", "CRIADO")))
                    .andExpect(jsonPath("$.resultados[0].codigo").value("DADOS_INVALIDOS"));
        } finally {
            for (String tabela : List.of("subponto", "aplicacao", "data_agendada", "agendamento", "ponto", "produto",
                    "usuario", "bairro")) {
                jdbc.update("delete from " + tabela + " where tenant_id = 'outro-sc'");
            }
            jdbc.update("delete from tenant where id = 'outro-sc'");
        }
    }
}
