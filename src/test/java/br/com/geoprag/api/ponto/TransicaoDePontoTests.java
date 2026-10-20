package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
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
 * {@code POST/DELETE /pontos/{id}/aplicador} e {@code POST /pontos/{id}/ativacao} sobre o seed de Gaspar:
 * PA-001 enderecada, PA-002 direcionada (aplicador 3), PA-003 ativa (3), PA-004 ativa (4), PA-005 inativa (4) e
 * PA-006 desativado (3).
 */
@TesteDeIntegracao
@Transactional
class TransicaoDePontoTests {

    private static final String PA_001 = "00000000-0000-4000-8000-000000000201";
    private static final String PA_002 = "00000000-0000-4000-8000-000000000202";
    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final String PA_005 = "00000000-0000-4000-8000-000000000205";
    private static final String PA_006 = "00000000-0000-4000-8000-000000000206";
    private static final String APLICADOR_3 = "00000000-0000-4000-8000-000000000003";
    private static final String APLICADOR_4 = "00000000-0000-4000-8000-000000000004";
    private static final String ADMIN = "00000000-0000-4000-8000-000000000001";
    private static final String APLICADOR_DESATIVADO = "00000000-0000-4000-8000-000000000005";

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

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    private ResultActions enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
            String authorization, String corpo) throws Exception {
        if (authorization != null) {
            req.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(req);
    }

    private ResultActions atribuir(String pontoId, String aplicadorId) throws Exception {
        return enviar(post("/api/v1/pontos/" + pontoId + "/aplicador"), tokens.bearerAdministrador(),
                "{\"aplicadorId\":\"" + aplicadorId + "\"}");
    }

    private ResultActions remover(String pontoId) throws Exception {
        return enviar(delete("/api/v1/pontos/" + pontoId + "/aplicador"), tokens.bearerAdministrador(), null);
    }

    private ResultActions ativar(String pontoId, String corpo) throws Exception {
        return enviar(post("/api/v1/pontos/" + pontoId + "/ativacao"), tokens.bearerAdministrador(), corpo);
    }

    private String ativacao(LocalDate inicio, int intervalo, int quantidade) {
        return "{\"dataInicio\":\"" + inicio + "\",\"intervaloDias\":" + intervalo + ",\"quantidadeRecorrencias\":"
                + quantidade + "}";
    }

    private ResultActions ler(String pontoId) throws Exception {
        return enviar(get("/api/v1/pontos/" + pontoId), tokens.bearerAdministrador(), null);
    }

    private String estadoNoBanco(String pontoId) {
        entityManager.flush();
        return jdbc.queryForObject("select estado || '|' || coalesce(estado_anterior, '-') || '|' || "
                + "coalesce(cast(aplicador_id as varchar), '-') from ponto where id = ?", String.class,
                UUID.fromString(pontoId));
    }

    private void recusado409(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("OPERACAO_NAO_PERMITIDA"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    // ---- atribuir / trocar ------------------------------------------------------------------------------------

    @Test
    void atribuirEmPontoEnderecadoDirecionaERefleteNoDetalhe() throws Exception {
        atribuir(PA_001, APLICADOR_3).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PA_001))
                .andExpect(jsonPath("$.estado").value("DIRECIONADA"))
                .andExpect(jsonPath("$.estadoAnterior").value("ENDERECADA"))
                .andExpect(jsonPath("$.aplicadorId").value(APLICADOR_3));

        ler(PA_001).andExpect(jsonPath("$.estado").value("DIRECIONADA"))
                .andExpect(jsonPath("$.aplicadorId").value(APLICADOR_3));
        assertThat(estadoNoBanco(PA_001)).isEqualTo("DIRECIONADA|ENDERECADA|" + APLICADOR_3);
    }

    @Test
    void trocarEmPontoDirecionadoMantemOEstado() throws Exception {
        atribuir(PA_002, APLICADOR_4).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DIRECIONADA"))
                .andExpect(jsonPath("$.estadoAnterior").value(nullValue()))
                .andExpect(jsonPath("$.aplicadorId").value(APLICADOR_4));
    }

    @Test
    void trocarEmPontoInativoMantemOEstado() throws Exception {
        atribuir(PA_005, APLICADOR_3).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INATIVA"))
                .andExpect(jsonPath("$.estadoAnterior").value("ATIVA"))
                .andExpect(jsonPath("$.aplicadorId").value(APLICADOR_3));
    }

    @Test
    void atribuirEmPontoAtivoOuDesativadoRetorna409ENaoAlteraDados() throws Exception {
        String ativaAntes = estadoNoBanco(PA_003);
        String desativadoAntes = estadoNoBanco(PA_006);

        recusado409(atribuir(PA_003, APLICADOR_4));
        recusado409(atribuir(PA_006, APLICADOR_4));

        assertThat(estadoNoBanco(PA_003)).isEqualTo(ativaAntes);
        assertThat(estadoNoBanco(PA_006)).isEqualTo(desativadoAntes);
    }

    @Test
    void aplicadorInvalidoRetorna400SemRevelarOMotivo() throws Exception {
        String antes = estadoNoBanco(PA_001);
        for (String invalido : new String[] { UUID.randomUUID().toString(), "nao-e-uuid", ADMIN }) {
            atribuir(PA_001, invalido).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                    .andExpect(jsonPath("$.errors[0].field").value("aplicadorId"));
        }
        assertThat(estadoNoBanco(PA_001)).isEqualTo(antes);
    }

    @Test
    void aplicadorDeOutroTenantRetorna400() throws Exception {
        String outroTenant = "outro-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into tenant (id, nome) values (?, 'Outro')", outroTenant);
        UUID doOutro = UUID.randomUUID();
        String cpf = String.format("%011d", Math.abs(doOutro.getMostSignificantBits() % 100_000_000_000L));
        jdbc.update("insert into usuario (id, tenant_id, nome, email, cpf, perfil, status, senha_hash) values "
                + "(?, ?, 'Ap Outro', ?, ?, 'APLICADOR', 'ATIVO', 'x')", doOutro, outroTenant,
                "ap." + doOutro + "@example.com", cpf);

        atribuir(PA_001, doOutro.toString()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("aplicadorId"));
        assertThat(estadoNoBanco(PA_001)).startsWith("ENDERECADA|");
    }

    @Test
    void aplicadorDesativadoRetorna409() throws Exception {
        recusado409(atribuir(PA_001, APLICADOR_DESATIVADO));
        assertThat(estadoNoBanco(PA_001)).startsWith("ENDERECADA|");
    }

    @Test
    void corpoSemAplicadorRetorna400() throws Exception {
        enviar(post("/api/v1/pontos/" + PA_001 + "/aplicador"), tokens.bearerAdministrador(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("aplicadorId"));
    }

    // ---- remover ----------------------------------------------------------------------------------------------

    @Test
    void removerEmPontoDirecionadoVoltaAEnderecada() throws Exception {
        remover(PA_002).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENDERECADA"))
                .andExpect(jsonPath("$.estadoAnterior").value("DIRECIONADA"))
                .andExpect(jsonPath("$.aplicadorId").value(nullValue()));
        assertThat(estadoNoBanco(PA_002)).isEqualTo("ENDERECADA|DIRECIONADA|-");
    }

    @Test
    void removerNosOutrosEstadosRetorna409EOPontoMantemOAplicador() throws Exception {
        for (String ponto : new String[] { PA_001, PA_003, PA_005, PA_006 }) {
            String antes = estadoNoBanco(ponto);
            recusado409(remover(ponto));
            assertThat(estadoNoBanco(ponto)).isEqualTo(antes);
        }
    }

    // ---- ativar -----------------------------------------------------------------------------------------------

    @Test
    void ativarPontoDirecionadoGeraAsDatasERefleteNoDetalhe() throws Exception {
        LocalDate inicio = hoje().plusDays(3);

        ativar(PA_002, ativacao(inicio, 7, 3)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.estadoAnterior").value("DIRECIONADA"))
                .andExpect(jsonPath("$.aplicadorId").value(APLICADOR_3))
                .andExpect(jsonPath("$.agendamento.dataInicio").value(inicio.toString()))
                .andExpect(jsonPath("$.agendamento.intervaloDias").value(7))
                .andExpect(jsonPath("$.agendamento.quantidadeRecorrencias").value(3))
                .andExpect(jsonPath("$.agendamento.datas", hasSize(3)))
                .andExpect(jsonPath("$.agendamento.datas[*].data",
                        contains(inicio.toString(), inicio.plusDays(7).toString(), inicio.plusDays(14).toString())))
                .andExpect(jsonPath("$.agendamento.datas[*].status", contains("PENDENTE", "PENDENTE", "PENDENTE")));

        ler(PA_002).andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.agendamento.datas", hasSize(3)));
        assertThat(jdbc.queryForObject("select count(*) from agendamento where ponto_id = ? and vigente = true",
                Integer.class, UUID.fromString(PA_002))).isEqualTo(1);
    }

    @Test
    void ativarComDataDeHojeEhAceito() throws Exception {
        ativar(PA_002, ativacao(hoje(), 1, 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamento.datas", hasSize(1)));
    }

    @Test
    void ativarPontoInativoCriaNovoAgendamentoVigenteEEncerraOAnterior() throws Exception {
        // PA-003 (ativa, agendamento vigente com datas pendentes) vira inativa por SQL: cenário do cancelamento
        jdbc.update("update ponto set estado = 'INATIVA', estado_anterior = 'ATIVA' where id = ?",
                UUID.fromString(PA_003));
        entityManager.clear();
        LocalDate inicio = hoje().plusDays(1);

        ativar(PA_003, ativacao(inicio, 2, 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.estadoAnterior").value("INATIVA"))
                .andExpect(jsonPath("$.agendamento.dataInicio").value(inicio.toString()))
                .andExpect(jsonPath("$.agendamento.datas", hasSize(2)));

        assertThat(jdbc.queryForObject("select count(*) from agendamento where ponto_id = ? and vigente = true",
                Integer.class, UUID.fromString(PA_003))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select vigente from agendamento where id = ?", Boolean.class,
                UUID.fromString("00000000-0000-4000-8000-000000000301"))).isFalse();
        // as datas pendentes do ciclo anterior foram canceladas; a concluída (se houver) é preservada
        assertThat(jdbc.queryForObject("select count(*) from data_agendada where agendamento_id = ? "
                + "and status = 'PENDENTE'", Integer.class, UUID.fromString("00000000-0000-4000-8000-000000000301")))
                .isZero();
    }

    @Test
    void ativarPontoInativoSemAgendamentoVigenteTambemFunciona() throws Exception {
        // PA-005 é inativa e o seu único agendamento já está encerrado (vigente = false) no seed
        ativar(PA_005, ativacao(hoje().plusDays(5), 7, 2)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATIVA"))
                .andExpect(jsonPath("$.agendamento.datas", hasSize(2)));
        assertThat(jdbc.queryForObject("select count(*) from agendamento where ponto_id = ?", Integer.class,
                UUID.fromString(PA_005))).isEqualTo(2);
    }

    @Test
    void ativarEmEstadoQueNaoPermiteRetorna409ENaoCriaAgendamento() throws Exception {
        int antes = jdbc.queryForObject("select count(*) from agendamento", Integer.class);

        recusado409(ativar(PA_001, ativacao(hoje().plusDays(1), 7, 2)));
        recusado409(ativar(PA_003, ativacao(hoje().plusDays(1), 7, 2)));
        recusado409(ativar(PA_006, ativacao(hoje().plusDays(1), 7, 2)));

        assertThat(jdbc.queryForObject("select count(*) from agendamento", Integer.class)).isEqualTo(antes);
        assertThat(estadoNoBanco(PA_001)).startsWith("ENDERECADA|");
    }

    @Test
    void ativarComDadosInvalidosRetorna400ENaoAlteraNada() throws Exception {
        int antes = jdbc.queryForObject("select count(*) from agendamento", Integer.class);

        ativar(PA_002, ativacao(hoje().minusDays(1), 7, 2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("dataInicio"));
        ativar(PA_002, ativacao(hoje(), 0, 2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("intervaloDias"));
        ativar(PA_002, ativacao(hoje(), 7, 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantidadeRecorrencias"));
        ativar(PA_002, ativacao(hoje(), 7, 366)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantidadeRecorrencias"));
        // intervalo sem máximo, mas datas além do ano 9999 não são aceitas
        ativar(PA_002, ativacao(hoje(), Integer.MAX_VALUE, 365)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("intervaloDias"));
        ativar(PA_002, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors", hasSize(3)));
        ativar(PA_002, "{\"dataInicio\":\"amanha\",\"intervaloDias\":7,\"quantidadeRecorrencias\":2}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUISICAO_INVALIDA"));

        assertThat(jdbc.queryForObject("select count(*) from agendamento", Integer.class)).isEqualTo(antes);
        assertThat(estadoNoBanco(PA_002)).startsWith("DIRECIONADA|");
    }

    @Test
    void intervaloGrandeMasDentroDoPeriodoEhAceito() throws Exception {
        ativar(PA_002, ativacao(hoje(), 3650, 3)).andExpect(status().isOk())
                .andExpect(jsonPath("$.agendamento.datas", hasSize(3)));
    }

    // ---- tenant e papel -------------------------------------------------------------------------------------

    @Test
    void pontoInexistenteOuDeOutroTenantRetorna404() throws Exception {
        String outroTenant = tokens.bearer(TokensDeTeste.ID_ADMINISTRADOR, "outro-sc", "ADMINISTRADOR");
        String corpoAplicador = "{\"aplicadorId\":\"" + APLICADOR_3 + "\"}";

        for (String id : new String[] { UUID.randomUUID().toString(), "nao-e-uuid" }) {
            atribuir(id, APLICADOR_3).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PONTO_NAO_ENCONTRADO"));
            remover(id).andExpect(status().isNotFound());
            ativar(id, ativacao(hoje(), 7, 1)).andExpect(status().isNotFound());
        }
        enviar(post("/api/v1/pontos/" + PA_001 + "/aplicador"), outroTenant, corpoAplicador)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PONTO_NAO_ENCONTRADO"));
        enviar(delete("/api/v1/pontos/" + PA_002 + "/aplicador"), outroTenant, null).andExpect(status().isNotFound());
        enviar(post("/api/v1/pontos/" + PA_002 + "/ativacao"), outroTenant, ativacao(hoje(), 7, 1))
                .andExpect(status().isNotFound());
        assertThat(estadoNoBanco(PA_001)).startsWith("ENDERECADA|");
    }

    @Test
    void subAdministradorPodeEAplicadorOuAnonimoNao() throws Exception {
        String sub = tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, "gaspar-sc", "SUB_ADMINISTRADOR");
        enviar(post("/api/v1/pontos/" + PA_001 + "/aplicador"), sub, "{\"aplicadorId\":\"" + APLICADOR_3 + "\"}")
                .andExpect(status().isOk());

        String aplicador = tokens.bearerAplicador();
        enviar(post("/api/v1/pontos/" + PA_002 + "/aplicador"), aplicador, "{\"aplicadorId\":\"" + APLICADOR_4 + "\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        enviar(delete("/api/v1/pontos/" + PA_002 + "/aplicador"), aplicador, null).andExpect(status().isForbidden());
        enviar(post("/api/v1/pontos/" + PA_002 + "/ativacao"), aplicador, ativacao(hoje(), 7, 1))
                .andExpect(status().isForbidden());

        enviar(post("/api/v1/pontos/" + PA_002 + "/aplicador"), null, "{\"aplicadorId\":\"" + APLICADOR_4 + "\"}")
                .andExpect(status().isUnauthorized());
        enviar(delete("/api/v1/pontos/" + PA_002 + "/aplicador"), null, null).andExpect(status().isUnauthorized());
        enviar(post("/api/v1/pontos/" + PA_002 + "/ativacao"), null, ativacao(hoje(), 7, 1))
                .andExpect(status().isUnauthorized());
        assertThat(estadoNoBanco(PA_002)).startsWith("DIRECIONADA|");
    }
}
