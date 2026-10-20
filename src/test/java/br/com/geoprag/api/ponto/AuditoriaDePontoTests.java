package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;
import br.com.geoprag.api.ponto.domain.AcaoDePonto;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.PontoAuditoria;
import br.com.geoprag.api.ponto.repository.PontoAuditoriaRepository;
import br.com.geoprag.api.ponto.service.AuditoriaDePontoService;
import br.com.geoprag.api.ponto.service.AuditoriaDePontoService.EventoDeAuditoria;

/** Trilha de auditoria dos pontos sobre o seed de Gaspar. */
@TesteDeIntegracao
@Transactional
class AuditoriaDePontoTests {

    private static final String GASPAR = "gaspar-sc";
    private static final String PA_001 = "00000000-0000-4000-8000-000000000201";
    private static final String PA_002 = "00000000-0000-4000-8000-000000000202";
    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final String BAIRRO_CENTRO = "00000000-0000-4000-8000-000000000101";
    private static final String ADMIN = TokensDeTeste.ID_ADMINISTRADOR;
    private static final String APLICADOR_3 = TokensDeTeste.ID_APLICADOR;
    private static final String APLICADOR_4 = "00000000-0000-4000-8000-000000000004";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private AuditoriaDePontoService auditoria;
    @Autowired
    private Clock relogio;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private String chamar(MockHttpServletRequestBuilder req, String authorization, String corpo, int statusEsperado)
            throws Exception {
        req.header(HttpHeaders.AUTHORIZATION, authorization);
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(req).andExpect(status().is(statusEsperado)).andReturn().getResponse()
                .getContentAsString();
    }

    private String admin() {
        return tokens.bearerAdministrador();
    }

    private String ativacao(int dias) {
        return "{\"dataInicio\":\"" + LocalDate.now(relogio).plusDays(dias)
                + "\",\"intervaloDias\":7,\"quantidadeRecorrencias\":2}";
    }

    private static String cadastro(String nome, String extras) {
        return "{\"nome\":\"" + nome + "\",\"bairroId\":\"" + BAIRRO_CENTRO + "\",\"endereco\":\"Rua A\","
                + "\"dosagemMl\":20,\"distanciaEntreSubpontosMetros\":10,\"quantidadeDeSubpontos\":8"
                + (extras == null ? "" : "," + extras) + "}";
    }

    private List<EventoDeAuditoria> historico(String pontoId) {
        entityManager.flush();
        entityManager.clear();
        return auditoria.historico(GASPAR, UUID.fromString(pontoId));
    }

    private static String idDe(String json) {
        return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void cadaTransicaoGeraUmEventoNaOrdemComAtorEstadosEAplicadores() throws Exception {
        Instant antes = Instant.now(relogio);
        String id = idDe(chamar(post("/api/v1/pontos"), admin(), cadastro("Auditado", null), 200));
        chamar(post("/api/v1/pontos/" + id + "/aplicador"), admin(), "{\"aplicadorId\":\"" + APLICADOR_3 + "\"}", 200);
        chamar(post("/api/v1/pontos/" + id + "/aplicador"), admin(), "{\"aplicadorId\":\"" + APLICADOR_4 + "\"}", 200);
        chamar(post("/api/v1/pontos/" + id + "/ativacao"), admin(), ativacao(2), 200);
        chamar(post("/api/v1/pontos/" + id + "/cancelamento"), admin(), null, 200);
        chamar(post("/api/v1/pontos/" + id + "/ativacao"), admin(), ativacao(3), 200);
        chamar(post("/api/v1/pontos/" + id + "/desativacao"), admin(), null, 200);
        chamar(post("/api/v1/pontos/" + id + "/reativacao"), admin(), null, 200);

        List<EventoDeAuditoria> eventos = historico(id);

        assertThat(eventos).extracting(EventoDeAuditoria::acao).containsExactly(AcaoDePonto.CRIACAO,
                AcaoDePonto.ATRIBUICAO_APLICADOR, AcaoDePonto.ATRIBUICAO_APLICADOR, AcaoDePonto.ATIVACAO,
                AcaoDePonto.CANCELAMENTO, AcaoDePonto.ATIVACAO, AcaoDePonto.DESATIVACAO, AcaoDePonto.REATIVACAO);
        assertThat(eventos).extracting(EventoDeAuditoria::atorId).containsOnly(UUID.fromString(ADMIN));
        assertThat(eventos).extracting(EventoDeAuditoria::estadoAnterior).containsExactly(null,
                EstadoPonto.ENDERECADA, EstadoPonto.DIRECIONADA, EstadoPonto.DIRECIONADA, EstadoPonto.ATIVA,
                EstadoPonto.INATIVA, EstadoPonto.ATIVA, EstadoPonto.DESATIVADO);
        assertThat(eventos).extracting(EventoDeAuditoria::estadoNovo).containsExactly(EstadoPonto.ENDERECADA,
                EstadoPonto.DIRECIONADA, EstadoPonto.DIRECIONADA, EstadoPonto.ATIVA, EstadoPonto.INATIVA,
                EstadoPonto.ATIVA, EstadoPonto.DESATIVADO, EstadoPonto.ATIVA);
        // a troca do aplicador registra quem era e quem passou a ser
        assertThat(eventos.get(1).aplicadorAnteriorId()).isNull();
        assertThat(eventos.get(1).aplicadorNovoId()).isEqualTo(UUID.fromString(APLICADOR_3));
        assertThat(eventos.get(2).aplicadorAnteriorId()).isEqualTo(UUID.fromString(APLICADOR_3));
        assertThat(eventos.get(2).aplicadorNovoId()).isEqualTo(UUID.fromString(APLICADOR_4));
        assertThat(eventos.get(3).detalhes()).contains("intervaloDias=7").contains("quantidadeRecorrencias=2");
        assertThat(eventos.get(4).detalhes()).isEqualTo("datasCanceladas=2");
        // data e hora do servidor, em ordem
        assertThat(eventos).extracting(EventoDeAuditoria::ocorridoEm)
                .allSatisfy(quando -> assertThat(quando).isBetween(antes.minusSeconds(1), Instant.now().plusSeconds(5)))
                .isSorted();
    }

    @Test
    void removerAplicadorEEdicaoGeramEventoComOConteudoDaMudanca() throws Exception {
        chamar(post("/api/v1/pontos/" + PA_002 + "/desativacao"), admin(), null, 200);
        chamar(post("/api/v1/pontos/" + PA_002 + "/reativacao"), admin(), null, 200);
        chamar(delete("/api/v1/pontos/" + PA_002 + "/aplicador"), admin(), null, 200);
        chamar(put("/api/v1/pontos/" + PA_002), admin(),
                cadastro("Nome novo", "\"numeroReferencia\":\"77\",\"toleranciaAtrasoDias\":6"), 200);

        List<EventoDeAuditoria> eventos = historico(PA_002);

        assertThat(eventos).extracting(EventoDeAuditoria::acao).containsExactly(AcaoDePonto.DESATIVACAO,
                AcaoDePonto.REATIVACAO, AcaoDePonto.REMOCAO_APLICADOR, AcaoDePonto.EDICAO);
        EventoDeAuditoria remocao = eventos.get(2);
        assertThat(remocao.estadoAnterior()).isEqualTo(EstadoPonto.DIRECIONADA);
        assertThat(remocao.estadoNovo()).isEqualTo(EstadoPonto.ENDERECADA);
        assertThat(remocao.aplicadorAnteriorId()).isEqualTo(UUID.fromString(APLICADOR_3));
        assertThat(remocao.aplicadorNovoId()).isNull();
        EventoDeAuditoria edicao = eventos.get(3);
        assertThat(edicao.estadoAnterior()).isEqualTo(EstadoPonto.ENDERECADA);
        assertThat(edicao.estadoNovo()).isEqualTo(EstadoPonto.ENDERECADA);
        assertThat(edicao.detalhes()).contains("nome: Rua das Palmeiras, trecho 2 -> Nome novo")
                .contains("numeroReferencia: (vazio) -> 77").contains("toleranciaAtrasoDias: 4 -> 6");
    }

    @Test
    void recusasEOperacoesSemEfeitoNaoGeramEvento() throws Exception {
        // 409: ativo não aceita novo aplicador; 400: aplicador inválido; 404: ponto inexistente
        chamar(post("/api/v1/pontos/" + PA_003 + "/aplicador"), admin(), "{\"aplicadorId\":\"" + APLICADOR_4 + "\"}",
                409);
        chamar(post("/api/v1/pontos/" + PA_001 + "/aplicador"), admin(), "{\"aplicadorId\":\"" + ADMIN + "\"}", 400);
        chamar(post("/api/v1/pontos/" + UUID.randomUUID() + "/desativacao"), admin(), null, 404);
        chamar(post("/api/v1/pontos/" + PA_003 + "/ativacao"), admin(), ativacao(1), 409);
        // sem efeito: repetir o mesmo aplicador e reenviar o cadastro igual
        chamar(post("/api/v1/pontos/" + PA_002 + "/aplicador"), admin(), "{\"aplicadorId\":\"" + APLICADOR_3 + "\"}",
                200);

        assertThat(historico(PA_003)).isEmpty();
        assertThat(historico(PA_001)).isEmpty();
        assertThat(historico(PA_002)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from ponto_auditoria", Integer.class)).isZero();
    }

    @Test
    void edicaoIdenticaAoCadastroNaoGeraEvento() throws Exception {
        String igual = "{\"nome\":\"Travessa da Colina\",\"bairroId\":\"00000000-0000-4000-8000-000000000102\","
                + "\"endereco\":\"Travessa da Colina\",\"numeroReferencia\":\"45\",\"dosagemMl\":25,"
                + "\"distanciaEntreSubpontosMetros\":5,\"quantidadeDeSubpontos\":6}";
        chamar(put("/api/v1/pontos/" + PA_003), admin(), igual, 200);

        assertThat(historico(PA_003)).isEmpty();
    }

    @Test
    void oAtorEOUsuarioDoTokenESomenteDoTenantDele() throws Exception {
        String sub = tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, GASPAR, "SUB_ADMINISTRADOR");
        chamar(post("/api/v1/pontos/" + PA_001 + "/desativacao"), sub, null, 200);

        List<EventoDeAuditoria> eventos = historico(PA_001);

        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).atorId()).isEqualTo(UUID.fromString(TokensDeTeste.ID_SUB_ADMINISTRADOR));
        assertThat(auditoria.historico("outro-sc", UUID.fromString(PA_001))).isEmpty();
    }

    @Test
    void aTrilhaNaoGuardaDadosPessoaisNemPermiteAlterarOuApagar() throws Exception {
        chamar(post("/api/v1/pontos/" + PA_001 + "/desativacao"), admin(), null, 200);
        entityManager.flush();

        List<String> colunas = jdbc.queryForList("select lower(column_name) from information_schema.columns "
                + "where lower(table_name) = 'ponto_auditoria'", String.class);
        assertThat(colunas).doesNotContain("cpf", "email", "nome", "senha_hash", "senha");
        assertThat(jdbc.queryForObject("select count(*) from ponto_auditoria where detalhes like '%@%'",
                Integer.class)).isZero();

        for (Method metodo : PontoAuditoriaRepository.class.getDeclaredMethods()) {
            assertThat(metodo.getName()).doesNotStartWith("delete").doesNotStartWith("remove")
                    .doesNotStartWith("update");
            if (!metodo.getName().equals("save")) {
                assertThat(metodo.getName()).startsWith("findByTenantId");
            }
        }
        assertThat(Arrays.stream(PontoAuditoria.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(nome -> nome.startsWith("set"));
    }

    @Test
    void oBancoRecusaEventoDePontoOuAtorDeOutroTenant() {
        String outro = "outro-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into tenant (id, nome) values (?, 'Outro')", outro);

        assertThatThrownBy(() -> jdbc.update("insert into ponto_auditoria (tenant_id, ponto_id, ator_id, acao, "
                + "estado_novo) values (?, ?, ?, 'EDICAO', 'ENDERECADA')", outro, UUID.fromString(PA_001),
                UUID.fromString(ADMIN))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into ponto_auditoria (tenant_id, ponto_id, ator_id, acao, "
                + "estado_novo) values (?, ?, ?, 'FAXINA', 'ENDERECADA')", GASPAR, UUID.fromString(PA_001),
                UUID.fromString(ADMIN))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void registrarExigeAtransacaoDaMudanca() {
        assertThatThrownBy(() -> auditoria.registrar(GASPAR, null, UUID.fromString(ADMIN), AcaoDePonto.EDICAO,
                EstadoPonto.ENDERECADA, null, null)).isInstanceOf(IllegalTransactionStateException.class);
    }
}
