package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;

/**
 * {@code POST /pontos/{id}/desativacao}, {@code /reativacao} e {@code /cancelamento} sobre o seed de Gaspar:
 * PA-001 enderecada, PA-002 direcionada, PA-003 ativa (agendamento 301, datas 401 concluída, 402 e 403 pendentes),
 * PA-004 ativa, PA-005 inativa e PA-006 desativado (estado anterior direcionada).
 */
@TesteDeIntegracao
@Transactional
class TransicoesExtrasTests {

    private static final String PA_001 = "00000000-0000-4000-8000-000000000201";
    private static final String PA_002 = "00000000-0000-4000-8000-000000000202";
    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final String PA_005 = "00000000-0000-4000-8000-000000000205";
    private static final String PA_006 = "00000000-0000-4000-8000-000000000206";
    private static final UUID AGENDAMENTO_PA_003 = UUID.fromString("00000000-0000-4000-8000-000000000301");

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

    private ResultActions acao(String acao, String pontoId, String authorization) throws Exception {
        var requisicao = post("/api/v1/pontos/" + pontoId + "/" + acao);
        if (authorization != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(requisicao);
    }

    private ResultActions acao(String acao, String pontoId) throws Exception {
        return acao(acao, pontoId, tokens.bearerAdministrador());
    }

    private void recusado409(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("OPERACAO_NAO_PERMITIDA"))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    private String estadoNoBanco(String pontoId) {
        entityManager.flush();
        return jdbc.queryForObject("select estado || '|' || coalesce(estado_anterior, '-') from ponto "
                + "where id = ?", String.class, UUID.fromString(pontoId));
    }

    private List<String> statusDasDatasDoPa003() {
        entityManager.flush();
        return jdbc.queryForList("select status from data_agendada where agendamento_id = ? order by id",
                String.class, AGENDAMENTO_PA_003);
    }

    /** 401 concluída (fica), 402 pendente já vencida (fica), 403 pendente futura (é cancelada). */
    private void prepararDatasDoPa003() {
        LocalDate hoje = LocalDate.now(relogio);
        jdbc.update("update data_agendada set data_prevista = ? where id = ?", hoje.minusDays(30),
                UUID.fromString("00000000-0000-4000-8000-000000000401"));
        jdbc.update("update data_agendada set data_prevista = ? where id = ?", hoje.minusDays(3),
                UUID.fromString("00000000-0000-4000-8000-000000000402"));
        jdbc.update("update data_agendada set data_prevista = ? where id = ?", hoje.plusDays(2),
                UUID.fromString("00000000-0000-4000-8000-000000000403"));
        entityManager.clear();
    }

    // ---- desativar --------------------------------------------------------------------------------------------

    @Test
    void desativarPontoAtivoGuardaOEstadoECancelaSoAsDatasFuturasPendentes() throws Exception {
        prepararDatasDoPa003();

        acao("desativacao", PA_003).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DESATIVADO"))
                .andExpect(jsonPath("$.estadoAnterior").value("ATIVA"))
                .andExpect(jsonPath("$.aplicadorId").value("00000000-0000-4000-8000-000000000003"));

        assertThat(estadoNoBanco(PA_003)).isEqualTo("DESATIVADO|ATIVA");
        // concluída (401) e pendente vencida (402) preservadas; a futura (403) é cancelada
        assertThat(statusDasDatasDoPa003()).containsExactly("CONCLUIDA", "PENDENTE", "CANCELADA");
        // o histórico é preservado: o agendamento continua existindo
        assertThat(jdbc.queryForObject("select count(*) from agendamento where id = ?", Integer.class,
                AGENDAMENTO_PA_003)).isEqualTo(1);
    }

    @Test
    void desativarFuncionaEmQualquerEstadoMenosDesativado() throws Exception {
        for (String ponto : new String[] { PA_001, PA_002, PA_005 }) {
            String antes = estadoNoBanco(ponto).split("\\|")[0];
            acao("desativacao", ponto).andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("DESATIVADO"))
                    .andExpect(jsonPath("$.estadoAnterior").value(antes));
        }
        recusado409(acao("desativacao", PA_006));
        assertThat(estadoNoBanco(PA_006)).isEqualTo("DESATIVADO|DIRECIONADA");
    }

    // ---- reativar ---------------------------------------------------------------------------------------------

    @Test
    void reativarDevolveOPontoAoEstadoAnteriorEOHistoricoDeTransicoesFicaCoerente() throws Exception {
        acao("reativacao", PA_006).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DIRECIONADA"))
                .andExpect(jsonPath("$.estadoAnterior").value("DESATIVADO"));
        assertThat(estadoNoBanco(PA_006)).isEqualTo("DIRECIONADA|DESATIVADO");
    }

    @Test
    void desativarEReativarPontoEnderecadoVoltaAEnderecada() throws Exception {
        acao("desativacao", PA_001).andExpect(status().isOk());
        acao("reativacao", PA_001).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENDERECADA"))
                .andExpect(jsonPath("$.aplicadorId").value(nullValue()));
    }

    @Test
    void reativarPontoAtivoNaoRestauraAsDatasCanceladas() throws Exception {
        prepararDatasDoPa003();
        acao("desativacao", PA_003).andExpect(status().isOk());

        acao("reativacao", PA_003).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.estadoAnterior").value("DESATIVADO"));

        assertThat(statusDasDatasDoPa003()).containsExactly("CONCLUIDA", "PENDENTE", "CANCELADA");
    }

    @Test
    void reativarForaDeDesativadoOuSemEstadoAnteriorRetorna409() throws Exception {
        for (String ponto : new String[] { PA_001, PA_002, PA_003, PA_005 }) {
            String antes = estadoNoBanco(ponto);
            recusado409(acao("reativacao", ponto));
            assertThat(estadoNoBanco(ponto)).isEqualTo(antes);
        }
        jdbc.update("update ponto set estado_anterior = null where id = ?", UUID.fromString(PA_006));
        entityManager.clear();

        recusado409(acao("reativacao", PA_006));
        assertThat(estadoNoBanco(PA_006)).isEqualTo("DESATIVADO|-");
    }

    // ---- cancelar ---------------------------------------------------------------------------------------------

    @Test
    void cancelarPontoAtivoVaiAInativaEncerraOAgendamentoECancelaAsDatasFuturas() throws Exception {
        prepararDatasDoPa003();

        acao("cancelamento", PA_003).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INATIVA"))
                .andExpect(jsonPath("$.estadoAnterior").value("ATIVA"))
                .andExpect(jsonPath("$.aplicadorId").value("00000000-0000-4000-8000-000000000003"))
                // sem vigente, o detalhe mostra o agendamento mais recente: o ciclo encerrado, como histórico
                .andExpect(jsonPath("$.agendamento.datas[0].status").value("CONCLUIDA"));

        assertThat(estadoNoBanco(PA_003)).isEqualTo("INATIVA|ATIVA");
        assertThat(jdbc.queryForObject("select vigente from agendamento where id = ?", Boolean.class,
                AGENDAMENTO_PA_003)).isFalse();
        assertThat(statusDasDatasDoPa003()).containsExactly("CONCLUIDA", "PENDENTE", "CANCELADA");
    }

    @Test
    void cancelarForaDeAtivaRetorna409ENaoAlteraNada() throws Exception {
        for (String ponto : new String[] { PA_001, PA_002, PA_005, PA_006 }) {
            String antes = estadoNoBanco(ponto);
            recusado409(acao("cancelamento", ponto));
            assertThat(estadoNoBanco(ponto)).isEqualTo(antes);
        }
    }

    @Test
    void pontoCancelado_podeSerAtivadoDeNovoPeloFluxoNormal() throws Exception {
        acao("cancelamento", PA_003).andExpect(status().isOk());
        String dataInicio = LocalDate.now(relogio).plusDays(1).toString();

        mockMvc.perform(post("/api/v1/pontos/" + PA_003 + "/ativacao")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerAdministrador())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dataInicio\":\"" + dataInicio + "\",\"intervaloDias\":7,\"quantidadeRecorrencias\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.estadoAnterior").value("INATIVA"));
    }

    // ---- tenant e papel ---------------------------------------------------------------------------------------

    @Test
    void pontoInexistenteOuDeOutroTenantRetorna404() throws Exception {
        String outroTenant = tokens.bearer(TokensDeTeste.ID_ADMINISTRADOR, "outro-sc", "ADMINISTRADOR");
        for (String acao : new String[] { "desativacao", "reativacao", "cancelamento" }) {
            acao(acao, UUID.randomUUID().toString()).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PONTO_NAO_ENCONTRADO"));
            acao(acao, "nao-e-uuid").andExpect(status().isNotFound());
            acao(acao, PA_003, outroTenant).andExpect(status().isNotFound());
        }
        assertThat(estadoNoBanco(PA_003)).isEqualTo("ATIVA|DIRECIONADA");
    }

    @Test
    void soAdministradorESubAdministrador() throws Exception {
        String sub = tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, "gaspar-sc", "SUB_ADMINISTRADOR");
        acao("desativacao", PA_001, sub).andExpect(status().isOk());

        for (String acao : new String[] { "desativacao", "reativacao", "cancelamento" }) {
            acao(acao, PA_003, tokens.bearerAplicador()).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
            acao(acao, PA_003, null).andExpect(status().isUnauthorized());
        }
        assertThat(estadoNoBanco(PA_003)).isEqualTo("ATIVA|DIRECIONADA");
    }
}
