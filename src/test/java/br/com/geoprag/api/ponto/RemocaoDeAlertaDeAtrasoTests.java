package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;

/**
 * {@code POST /pontos/{id}/remocao-de-alerta} e o reflexo no pull, sobre o seed de Gaspar: PA-003 ativa, tolerância
 * de 4 dias, agendamento 301 com as datas 401 (concluída), 402 e 403 (pendentes), do aplicador 3.
 */
@TesteDeIntegracao
@Transactional
@TestPropertySource(properties = "app.sync.margem-cursor-segundos=0")
class RemocaoDeAlertaDeAtrasoTests {

    private static final String PA_002 = "00000000-0000-4000-8000-000000000202";
    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final UUID DATA_401 = UUID.fromString("00000000-0000-4000-8000-000000000401");
    private static final UUID DATA_402 = UUID.fromString("00000000-0000-4000-8000-000000000402");
    private static final UUID DATA_403 = UUID.fromString("00000000-0000-4000-8000-000000000403");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private Clock relogio;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private ResultActions remover(String pontoId, String authorization) throws Exception {
        var requisicao = post("/api/v1/pontos/" + pontoId + "/remocao-de-alerta");
        if (authorization != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(requisicao);
    }

    private ResultActions remover(String pontoId) throws Exception {
        return remover(pontoId, tokens.bearerAdministrador());
    }

    private void recusado409(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("OPERACAO_NAO_PERMITIDA"));
    }

    /** 402 é a recorrência pendente mais antiga: {@code diasDeAtraso} dias antes de hoje; 403 vem depois dela. */
    private void datas(int diasDeAtraso) {
        LocalDate hoje = LocalDate.now(relogio);
        jdbc.update("update data_agendada set data_prevista = ? where id = ?", hoje.minusDays(40), DATA_401);
        jdbc.update("update data_agendada set data_prevista = ? where id = ?", hoje.minusDays(diasDeAtraso),
                DATA_402);
        jdbc.update("update data_agendada set data_prevista = ? where id = ?", hoje.plusDays(7), DATA_403);
        entityManager.clear();
    }

    private Integer eventosDeRemocao() {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from ponto_auditoria where acao = 'REMOCAO_ALERTA_ATRASO' "
                + "and ponto_id = ?", Integer.class, UUID.fromString(PA_003));
    }

    private Object alertaNoBanco(UUID data) {
        entityManager.flush();
        return jdbc.queryForObject("select alerta_removido_em from data_agendada where id = ?", Object.class, data);
    }

    @Test
    void removerEmPontoAtrasadoDevolveOPontoAtualizadoEGravaAuditoria() throws Exception {
        datas(10);

        String corpo = remover(PA_003).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PA_003))
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.aplicadorId").value(TokensDeTeste.ID_APLICADOR))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<List<String>>read(corpo, "$.agendamento.datas[?(@.id=='" + DATA_402
                + "')].alertaRemovidoEm")).singleElement().isNotNull();
        assertThat(JsonPath.<List<String>>read(corpo, "$.agendamento.datas[?(@.id=='" + DATA_403
                + "')].alertaRemovidoEm")).singleElement().isNull();
        assertThat(alertaNoBanco(DATA_402)).isNotNull();
        assertThat(alertaNoBanco(DATA_403)).isNull();

        assertThat(eventosDeRemocao()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select ator_id, estado_anterior, estado_novo, aplicador_novo_id, detalhes "
                + "from ponto_auditoria where acao = 'REMOCAO_ALERTA_ATRASO'"))
                .containsEntry("ESTADO_ANTERIOR", "ATIVA").containsEntry("ESTADO_NOVO", "ATIVA")
                .containsEntry("ATOR_ID", UUID.fromString(TokensDeTeste.ID_ADMINISTRADOR))
                .satisfies(e -> assertThat((String) e.get("DETALHES")).contains(DATA_402.toString()));
    }

    @Test
    void subAdministradorTambemPodeEOutroPerfilNao() throws Exception {
        datas(10);

        remover(PA_003, tokens.bearerAplicador()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        remover(PA_003, null).andExpect(status().isUnauthorized());
        assertThat(eventosDeRemocao()).isZero();

        remover(PA_003, tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, "gaspar-sc", "SUB_ADMINISTRADOR"))
                .andExpect(status().isOk());
    }

    @Test
    void foraDeAtrasoERecusadoSemAlterarNada() throws Exception {
        datas(4); // último dia da tolerância: ainda em dia

        recusado409(remover(PA_003));
        assertThat(alertaNoBanco(DATA_402)).isNull();
        assertThat(eventosDeRemocao()).isZero();
    }

    @Test
    void noDiaSeguinteAoUltimoDiaDaToleranciaJaEhAtraso() throws Exception {
        datas(5);

        remover(PA_003).andExpect(status().isOk());
    }

    @Test
    void pontoQueNaoEstaAtivoOuNaoExisteERecusado() throws Exception {
        recusado409(remover(PA_002));
        remover(UUID.randomUUID().toString()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PONTO_NAO_ENCONTRADO"));
        remover("nao-e-uuid").andExpect(status().isNotFound());
        remover(PA_003, tokens.bearer(TokensDeTeste.ID_ADMINISTRADOR, "outro-sc", "ADMINISTRADOR"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PONTO_NAO_ENCONTRADO"));
    }

    @Test
    void removerDeNovoComOAlertaJaRemovidoERecusado() throws Exception {
        datas(10);

        remover(PA_003).andExpect(status().isOk());
        recusado409(remover(PA_003));
        assertThat(eventosDeRemocao()).isEqualTo(1);
    }

    @Test
    void aRemocaoValeSoParaOAtrasoAtualEAProximaRecorrenciaAlertaDeNovo() throws Exception {
        datas(10);
        remover(PA_003).andExpect(status().isOk());

        // 402 é cumprida e 403 também atrasa: é outra recorrência, sem remoção, e o alerta volta
        jdbc.update("update data_agendada set status = 'CONCLUIDA' where id = ?", DATA_402);
        jdbc.update("update data_agendada set data_prevista = ? where id = ?",
                LocalDate.now(relogio).minusDays(6), DATA_403);
        entityManager.clear();

        remover(PA_003).andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamento.datas[?(@.id=='" + DATA_403 + "')].alertaRemovidoEm",
                        hasSize(1)));
        assertThat(alertaNoBanco(DATA_403)).isNotNull();
        assertThat(eventosDeRemocao()).isEqualTo(2);
    }

    @Test
    void oPullSnapshotEOIncrementalRefletemARemocao() throws Exception {
        datas(10);
        String aplicador = tokens.bearerAplicador();
        String cursor = JsonPath.read(mockMvc.perform(get("/api/v1/sync/pull")
                .header(HttpHeaders.AUTHORIZATION, aplicador)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.cursor");
        Thread.sleep(5);

        remover(PA_003).andExpect(status().isOk());

        // incremental: o ponto alterado vai em `pontos`; `removidos` não é usado
        mockMvc.perform(get("/api/v1/sync/pull").param("desde", cursor).header(HttpHeaders.AUTHORIZATION, aplicador))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].id").value(PA_003))
                .andExpect(jsonPath("$.removidos", hasSize(0)))
                .andExpect(jsonPath("$.pontos[0].agendamento.datas[?(@.id=='" + DATA_402
                        + "')].alertaRemovidoEm", hasSize(1)));

        // snapshot: a recorrência atrasada vem com o campo; a seguinte, sem
        String snapshot = mockMvc.perform(get("/api/v1/sync/pull").header(HttpHeaders.AUTHORIZATION, aplicador))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(snapshot, "$.pontos[0].agendamento.datas[?(@.id=='" + DATA_402
                + "')].alertaRemovidoEm")).singleElement().isNotNull();
        assertThat(JsonPath.<List<String>>read(snapshot, "$.pontos[0].agendamento.datas[?(@.id=='" + DATA_403
                + "')].alertaRemovidoEm")).singleElement().isNull();
    }
}
