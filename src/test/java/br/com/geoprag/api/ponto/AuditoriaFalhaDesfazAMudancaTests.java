package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;
import br.com.geoprag.api.ponto.service.AuditoriaDePontoService;

/**
 * Falha ao gravar a auditoria desfaz a mudança. Sem transação de teste: cada pedido confirma ou desfaz de verdade,
 * então o que o banco mostra depois é o que um cliente veria. Nada é gravado enquanto a auditoria falha, portanto
 * não há o que restaurar.
 */
@TesteDeIntegracao
class AuditoriaFalhaDesfazAMudancaTests {

    private static final String PA_001 = "00000000-0000-4000-8000-000000000201";
    private static final String PA_002 = "00000000-0000-4000-8000-000000000202";
    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final String APLICADOR_4 = "00000000-0000-4000-8000-000000000004";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private AuditoriaDePontoService auditoria;

    private void auditoriaFalha() {
        // o bean injetado é o proxy transacional; o espião (Mockito) está atrás dele
        AuditoriaDePontoService espiao = AopTestUtils.getUltimateTargetObject(auditoria);
        doThrow(new IllegalStateException("falha simulada na auditoria")).when(espiao)
                .registrar(any(), any(), any(), any(), any(), any(), any());
    }

    private int pedido(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String corpo)
            throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, tokens.bearerAdministrador());
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(req).andReturn().getResponse().getStatus();
    }

    private String estado(String pontoId) {
        return jdbc.queryForObject("select estado || '|' || coalesce(cast(aplicador_id as varchar), '-') "
                + "from ponto where id = ?", String.class, UUID.fromString(pontoId));
    }

    @Test
    void atribuirComFalhaNaAuditoriaNaoMudaOPonto() throws Exception {
        String antes = estado(PA_001);
        auditoriaFalha();

        assertThat(pedido(post("/api/v1/pontos/" + PA_001 + "/aplicador"),
                "{\"aplicadorId\":\"" + APLICADOR_4 + "\"}")).isEqualTo(500);

        assertThat(estado(PA_001)).isEqualTo(antes).startsWith("ENDERECADA|");
        assertThat(jdbc.queryForObject("select count(*) from ponto_auditoria", Integer.class)).isZero();
    }

    @Test
    void ativarComFalhaNaAuditoriaNaoCriaAgendamentoNemMudaOPonto() throws Exception {
        int agendamentosAntes = jdbc.queryForObject("select count(*) from agendamento", Integer.class);
        auditoriaFalha();

        assertThat(pedido(post("/api/v1/pontos/" + PA_002 + "/ativacao"), "{\"dataInicio\":\""
                + LocalDate.now().plusDays(3) + "\",\"intervaloDias\":7,\"quantidadeRecorrencias\":3}"))
                .isEqualTo(500);

        assertThat(estado(PA_002)).startsWith("DIRECIONADA|");
        assertThat(jdbc.queryForObject("select count(*) from agendamento", Integer.class))
                .isEqualTo(agendamentosAntes);
    }

    @Test
    void cancelarComFalhaNaAuditoriaMantemAtivoEComAsDatasPendentes() throws Exception {
        auditoriaFalha();

        assertThat(pedido(post("/api/v1/pontos/" + PA_003 + "/cancelamento"), null)).isEqualTo(500);

        assertThat(estado(PA_003)).startsWith("ATIVA|");
        assertThat(jdbc.queryForObject("select vigente from agendamento where id = ?", Boolean.class,
                UUID.fromString("00000000-0000-4000-8000-000000000301"))).isTrue();
    }

    @Test
    void criarComFalhaNaAuditoriaNaoGravaOPonto() throws Exception {
        int antes = jdbc.queryForObject("select count(*) from ponto", Integer.class);
        auditoriaFalha();

        assertThat(pedido(post("/api/v1/pontos"), "{\"nome\":\"N\",\"bairroId\":\"00000000-0000-4000-8000-000000000101\","
                + "\"endereco\":\"Rua\",\"dosagemMl\":1,\"distanciaEntreSubpontosMetros\":1,"
                + "\"quantidadeDeSubpontos\":1}")).isEqualTo(500);

        assertThat(jdbc.queryForObject("select count(*) from ponto", Integer.class)).isEqualTo(antes);
    }

    @Test
    void semAFalhaOMesmoPedidoFuncionaEGravaOEvento() throws Exception {
        mockMvc.perform(post("/api/v1/pontos/" + PA_001 + "/desativacao")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerAdministrador())).andExpect(status().isOk());
        try {
            assertThat(jdbc.queryForObject("select count(*) from ponto_auditoria where ponto_id = ?", Integer.class,
                    UUID.fromString(PA_001))).isEqualTo(1);
        } finally {
            // este teste confirma de verdade: devolve o PA-001 ao seed e remove o evento
            jdbc.update("delete from ponto_auditoria where ponto_id = ?", UUID.fromString(PA_001));
            jdbc.update("update ponto set estado = 'ENDERECADA', estado_anterior = null where id = ?",
                    UUID.fromString(PA_001));
        }
    }
}
