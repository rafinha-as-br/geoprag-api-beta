package br.com.geoprag.api.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;
import br.com.geoprag.api.auth.repository.TenantRepository;
import br.com.geoprag.api.produto.domain.Produto;
import br.com.geoprag.api.produto.repository.ProdutoRepository;
import br.com.geoprag.api.sync.dto.ItemDeRegistro;
import br.com.geoprag.api.sync.dto.StatusItem;
import br.com.geoprag.api.sync.service.RegistroAplicacaoService;

/**
 * {@code GET /api/v1/sync/pull?desde=...} (incremental) sobre o seed de Gaspar. A margem do cursor é zerada aqui para
 * que "desde o cursor" signifique "desde o pull anterior"; a margem tem teste próprio. Aplicador 3 tem o PA-003
 * (ativo) e o aplicador 4 tem o PA-004 (ativo).
 */
@TesteDeIntegracao
@Transactional
@TestPropertySource(properties = "app.sync.margem-cursor-segundos=0")
class PullIncrementalTests {

    private static final String PA_002 = "00000000-0000-4000-8000-000000000202";
    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final String APLICADOR_3 = "00000000-0000-4000-8000-000000000003";
    private static final String APLICADOR_4 = "00000000-0000-4000-8000-000000000004";
    private static final String PRODUTO = "00000000-0000-4000-8000-000000000501";
    private static final String DATA_PA_003 = "00000000-0000-4000-8000-000000000402";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private RegistroAplicacaoService registro;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private ProdutoRepository produtos;
    @Autowired
    private Clock relogio;
    @Autowired
    private EntityManager entityManager;

    private String aplicador(String id) {
        return tokens.bearer(id, "gaspar-sc", "APLICADOR");
    }

    private ResultActions pull(String authorization, String desde) throws Exception {
        var requisicao = get("/api/v1/sync/pull");
        if (desde != null) {
            requisicao.param("desde", desde);
        }
        if (authorization != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(requisicao);
    }

    /** Snapshot do aplicador; o cursor dele é o {@code desde} do próximo pull. */
    private String cursorDoSnapshot(String id) throws Exception {
        String corpo = pull(aplicador(id), null).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        passaOTempo();
        return JsonPath.read(corpo, "$.cursor");
    }

    private ResultActions incremental(String id, String cursor) throws Exception {
        return pull(aplicador(id), cursor).andExpect(status().isOk());
    }

    private void admin(MockHttpServletRequestBuilder req, String corpo, int esperado) throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, tokens.bearerAdministrador());
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        mockMvc.perform(req).andExpect(status().is(esperado));
    }

    /** O relógio precisa andar além do instante do cursor antes da mudança (o cursor é truncado em microssegundos). */
    private static void passaOTempo() throws InterruptedException {
        Thread.sleep(5);
    }

    private String ativacao(int dias) {
        return "{\"dataInicio\":\"" + LocalDate.now(relogio).plusDays(dias)
                + "\",\"intervaloDias\":7,\"quantidadeRecorrencias\":2}";
    }

    private void vazio(ResultActions resposta) throws Exception {
        resposta.andExpect(jsonPath("$.pontos", hasSize(0))).andExpect(jsonPath("$.produtos", hasSize(0)))
                .andExpect(jsonPath("$.removidos", hasSize(0)));
    }

    // ---- formato ------------------------------------------------------------------------------------------------

    @Test
    void oSnapshotTrazOCursorEOsRemovidosVazios() throws Exception {
        String corpo = pull(aplicador(APLICADOR_3), null).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();

        OffsetDateTime cursor = OffsetDateTime.parse(JsonPath.<String>read(corpo, "$.cursor"));
        assertThat(cursor).isBeforeOrEqualTo(OffsetDateTime.now().plusSeconds(1))
                .isAfter(OffsetDateTime.now().minusMinutes(1));
        assertThat(JsonPath.<java.util.List<?>>read(corpo, "$.removidos")).isEmpty();
        assertThat(JsonPath.<java.util.List<?>>read(corpo, "$.pontos")).hasSize(1);
    }

    @Test
    void semAlteracoesARespostaDeAlteracoesEVaziaMasTrazOProximoCursor() throws Exception {
        String cursor = cursorDoSnapshot(APLICADOR_3);

        vazio(incremental(APLICADOR_3, cursor).andExpect(jsonPath("$.cursor").isNotEmpty()));
    }

    // ---- o que mudou ---------------------------------------------------------------------------------------------

    @Test
    void pontoEditadoVoltaComOsDadosNovosEOutroAplicadorNaoVe() throws Exception {
        String cursor3 = cursorDoSnapshot(APLICADOR_3);
        String cursor4 = cursorDoSnapshot(APLICADOR_4);
        String igualAoCadastro = "{\"nome\":\"Nome novo\",\"bairroId\":\"00000000-0000-4000-8000-000000000102\","
                + "\"endereco\":\"Travessa da Colina\",\"numeroReferencia\":\"45\",\"dosagemMl\":25,"
                + "\"distanciaEntreSubpontosMetros\":5,\"quantidadeDeSubpontos\":6}";
        admin(put("/api/v1/pontos/" + PA_003), igualAoCadastro, 200);

        incremental(APLICADOR_3, cursor3).andExpect(jsonPath("$.pontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].id").value(PA_003))
                .andExpect(jsonPath("$.pontos[0].nome").value("Nome novo"))
                .andExpect(jsonPath("$.pontos[0].agendamento.datas", hasSize(3)))
                .andExpect(jsonPath("$.removidos", hasSize(0)));
        vazio(incremental(APLICADOR_4, cursor4));
    }

    @Test
    void pontoAtivadoParaOAplicadorApareceNasAlteracoes() throws Exception {
        String cursor = cursorDoSnapshot(APLICADOR_3);
        admin(post("/api/v1/pontos/" + PA_002 + "/ativacao"), ativacao(2), 200);

        incremental(APLICADOR_3, cursor).andExpect(jsonPath("$.pontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].id").value(PA_002))
                .andExpect(jsonPath("$.pontos[0].estado").value("ATIVA"))
                .andExpect(jsonPath("$.pontos[0].agendamento.datas", hasSize(2)))
                .andExpect(jsonPath("$.removidos", hasSize(0)));
    }

    @Test
    void registroDeSubpontoFazOPontoVoltarComOSubponto() throws Exception {
        String cursor3 = cursorDoSnapshot(APLICADOR_3);
        String cursor4 = cursorDoSnapshot(APLICADOR_4);
        ItemDeRegistro item = new ItemDeRegistro(UUID.randomUUID().toString(), UUID.randomUUID().toString(), PA_003,
                DATA_PA_003, PRODUTO, new BigDecimal("-26.931700"), new BigDecimal("-48.958900"),
                OffsetDateTime.of(2026, 10, 12, 14, 30, 0, 0, ZoneOffset.ofHours(-3)));
        assertThat(registro.registrar("gaspar-sc", UUID.fromString(APLICADOR_3), item).status())
                .isEqualTo(StatusItem.CRIADO);
        entityManager.flush();
        entityManager.clear();

        incremental(APLICADOR_3, cursor3).andExpect(jsonPath("$.pontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].id").value(PA_003))
                .andExpect(jsonPath("$.pontos[0].subpontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].subpontos[0].id").value(item.id()));
        vazio(incremental(APLICADOR_4, cursor4));
    }

    @Test
    void produtoNovoApareceNasAlteracoesEOAntigoNao() throws Exception {
        String cursor = cursorDoSnapshot(APLICADOR_3);
        produtos.save(new Produto(tenants.getReferenceById("gaspar-sc"), "Produto novo"));
        entityManager.flush();

        incremental(APLICADOR_3, cursor).andExpect(jsonPath("$.produtos", hasSize(1)))
                .andExpect(jsonPath("$.produtos[0].nome").value("Produto novo"))
                .andExpect(jsonPath("$.pontos", hasSize(0)));
    }

    // ---- removidos -----------------------------------------------------------------------------------------------

    @Test
    void cancelarOPontoApareceComoRemocaoSoParaOAplicadorDele() throws Exception {
        String cursor3 = cursorDoSnapshot(APLICADOR_3);
        String cursor4 = cursorDoSnapshot(APLICADOR_4);
        admin(post("/api/v1/pontos/" + PA_003 + "/cancelamento"), null, 200);

        incremental(APLICADOR_3, cursor3).andExpect(jsonPath("$.pontos", hasSize(0)))
                .andExpect(jsonPath("$.removidos", contains(PA_003)));
        vazio(incremental(APLICADOR_4, cursor4));
    }

    @Test
    void desativarOPontoApareceComoRemocao() throws Exception {
        String cursor = cursorDoSnapshot(APLICADOR_3);
        admin(post("/api/v1/pontos/" + PA_003 + "/desativacao"), null, 200);

        incremental(APLICADOR_3, cursor).andExpect(jsonPath("$.pontos", hasSize(0)))
                .andExpect(jsonPath("$.removidos", contains(PA_003)));
    }

    @Test
    void pontoQueVoltouASerAtivoDoMesmoAplicadorVaiEmPontosENuncaEmRemovidos() throws Exception {
        String cursor = cursorDoSnapshot(APLICADOR_3);
        admin(post("/api/v1/pontos/" + PA_003 + "/desativacao"), null, 200);
        admin(post("/api/v1/pontos/" + PA_003 + "/reativacao"), null, 200);

        incremental(APLICADOR_3, cursor).andExpect(jsonPath("$.pontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].id").value(PA_003))
                .andExpect(jsonPath("$.removidos", hasSize(0)));
    }

    @Test
    void reatribuicaoApareceComoRemocaoParaOAnteriorEComoAlteracaoParaONovo() throws Exception {
        String cursor3 = cursorDoSnapshot(APLICADOR_3);
        String cursor4 = cursorDoSnapshot(APLICADOR_4);
        admin(post("/api/v1/pontos/" + PA_003 + "/cancelamento"), null, 200);
        admin(post("/api/v1/pontos/" + PA_003 + "/aplicador"), "{\"aplicadorId\":\"" + APLICADOR_4 + "\"}", 200);
        admin(post("/api/v1/pontos/" + PA_003 + "/ativacao"), ativacao(1), 200);

        incremental(APLICADOR_3, cursor3).andExpect(jsonPath("$.pontos", hasSize(0)))
                .andExpect(jsonPath("$.removidos", contains(PA_003)));
        incremental(APLICADOR_4, cursor4).andExpect(jsonPath("$.pontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].id").value(PA_003))
                .andExpect(jsonPath("$.pontos[0].aplicadorId").value(APLICADOR_4))
                .andExpect(jsonPath("$.removidos", hasSize(0)));
    }

    // ---- o parâmetro desde ----------------------------------------------------------------------------------------

    @Test
    void desdeMuitoAntigoDevolveTudoQueEstaAtivoEDesdeNoFuturoDevolveVazio() throws Exception {
        incremental(APLICADOR_3, "1970-01-01T00:00:00Z").andExpect(jsonPath("$.pontos", hasSize(1)))
                .andExpect(jsonPath("$.produtos", hasSize(1))).andExpect(jsonPath("$.removidos", hasSize(0)));
        vazio(incremental(APLICADOR_3, OffsetDateTime.now().plusDays(1).toString()));
    }

    @Test
    void desdeAceitaOffsetComMaisEComEspacoNoLugarDoMais() throws Exception {
        incremental(APLICADOR_3, "2999-01-01T00:00:00+03:00").andExpect(jsonPath("$.pontos", hasSize(0)));
        // o + sem codificação chega como espaço: tolerado
        incremental(APLICADOR_3, "2999-01-01T00:00:00 03:00").andExpect(jsonPath("$.pontos", hasSize(0)));
        // desde vazio equivale a sem desde (snapshot)
        incremental(APLICADOR_3, "").andExpect(jsonPath("$.pontos", hasSize(1)));
    }

    @Test
    void desdeForaDoFormatoRetorna400() throws Exception {
        for (String invalido : new String[] { "ontem", "2026-10-21", "2026-10-21T08:00:00", "1761044400000" }) {
            pull(aplicador(APLICADOR_3), invalido).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                    .andExpect(jsonPath("$.errors[0].field").value("desde"));
        }
    }

    // ---- segurança --------------------------------------------------------------------------------------------------

    @Test
    void soOAplicadorEDoTenantDoToken() throws Exception {
        pull(tokens.bearerAdministrador(), "1970-01-01T00:00:00Z").andExpect(status().isForbidden());
        pull(null, "1970-01-01T00:00:00Z").andExpect(status().isUnauthorized());
        String outroTenant = tokens.bearer(APLICADOR_3, "outro-sc", "APLICADOR");

        vazio(pull(outroTenant, "1970-01-01T00:00:00Z").andExpect(status().isOk()));
    }
}
