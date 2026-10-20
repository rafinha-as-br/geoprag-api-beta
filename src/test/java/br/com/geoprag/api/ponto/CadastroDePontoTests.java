package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;
import br.com.geoprag.api.sync.dto.ItemDeRegistro;
import br.com.geoprag.api.sync.dto.StatusItem;
import br.com.geoprag.api.sync.service.RegistroAplicacaoService;

/** {@code POST /pontos} e {@code PUT /pontos/{id}} sobre o seed de Gaspar (PA-001 a PA-006). */
@TesteDeIntegracao
@Transactional
class CadastroDePontoTests {

    private static final String PA_001 = "00000000-0000-4000-8000-000000000201";
    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final String PA_005 = "00000000-0000-4000-8000-000000000205";
    private static final String BAIRRO_CENTRO = "00000000-0000-4000-8000-000000000101";
    private static final String BAIRRO_BELA_VISTA = "00000000-0000-4000-8000-000000000102";
    private static final String APLICADOR_3 = "00000000-0000-4000-8000-000000000003";
    private static final String APLICADOR_DESATIVADO = "00000000-0000-4000-8000-000000000005";
    private static final String ADMIN = "00000000-0000-4000-8000-000000000001";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RegistroAplicacaoService registro;

    private ResultActions enviar(MockHttpServletRequestBuilder req, String authorization, String corpo)
            throws Exception {
        if (authorization != null) {
            req.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mockMvc.perform(req);
    }

    private ResultActions criar(String corpo) throws Exception {
        return enviar(post("/api/v1/pontos"), tokens.bearerAdministrador(), corpo);
    }

    private ResultActions editar(String pontoId, String corpo) throws Exception {
        return enviar(put("/api/v1/pontos/" + pontoId), tokens.bearerAdministrador(), corpo);
    }

    /** Corpo mínimo válido, com os campos pedidos acrescentados ou trocados. */
    private static String corpo(String nome, String bairroId, String extras) {
        return "{\"nome\":\"" + nome + "\",\"bairroId\":\"" + bairroId + "\",\"endereco\":\"Rua Nova\","
                + "\"dosagemMl\":20.5,\"distanciaEntreSubpontosMetros\":10,\"quantidadeDeSubpontos\":8"
                + (extras == null ? "" : "," + extras) + "}";
    }

    private int contagemDePontos() {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from ponto", Integer.class);
    }

    // ---- criar ----------------------------------------------------------------------------------------------

    @Test
    void criarSoComOMinimoGeraOIdentificadorEOPontoNasceEnderecada() throws Exception {
        criar(corpo("Rua Nova, trecho 1", BAIRRO_CENTRO, null)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.identificador").value("PA-007"))
                .andExpect(jsonPath("$.nome").value("Rua Nova, trecho 1"))
                .andExpect(jsonPath("$.bairro").value("Centro"))
                .andExpect(jsonPath("$.bairroId").value(BAIRRO_CENTRO))
                .andExpect(jsonPath("$.endereco").value("Rua Nova"))
                .andExpect(jsonPath("$.dosagemMl").value(20.5))
                .andExpect(jsonPath("$.distanciaEntreSubpontosMetros").value(10))
                .andExpect(jsonPath("$.quantidadeDeSubpontos").value(8))
                .andExpect(jsonPath("$.toleranciaAtrasoDias").value(4))
                .andExpect(jsonPath("$.estado").value("ENDERECADA"))
                .andExpect(jsonPath("$.estadoAnterior").value(nullValue()))
                .andExpect(jsonPath("$.aplicadorId").value(nullValue()))
                .andExpect(jsonPath("$.larguraMetros").value(nullValue()))
                .andExpect(jsonPath("$.agendamento").value(nullValue()))
                .andExpect(jsonPath("$.subpontos", hasSize(0)));
    }

    @Test
    void criarComAplicadorNasceDirecionadaEOsIdentificadoresSeguemASequencia() throws Exception {
        criar(corpo("Primeiro", BAIRRO_BELA_VISTA, "\"aplicadorId\":\"" + APLICADOR_3 + "\","
                + "\"toleranciaAtrasoDias\":7,\"numeroReferencia\":\"120\",\"descricaoDoTrecho\":\"Do A ao B\","
                + "\"larguraMetros\":1.5,\"profundidadeMetros\":0.3,\"velocidadeMetrosPorSegundo\":0.8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificador").value("PA-007"))
                .andExpect(jsonPath("$.estado").value("DIRECIONADA"))
                .andExpect(jsonPath("$.aplicadorId").value(APLICADOR_3))
                .andExpect(jsonPath("$.toleranciaAtrasoDias").value(7))
                .andExpect(jsonPath("$.numeroReferencia").value("120"))
                .andExpect(jsonPath("$.descricaoDoTrecho").value("Do A ao B"))
                .andExpect(jsonPath("$.larguraMetros").value(1.5))
                .andExpect(jsonPath("$.profundidadeMetros").value(0.3))
                .andExpect(jsonPath("$.velocidadeMetrosPorSegundo").value(0.8));
        criar(corpo("Segundo", BAIRRO_CENTRO, null)).andExpect(jsonPath("$.identificador").value("PA-008"));
    }

    @Test
    void criarApareceNoDetalheENaListagem() throws Exception {
        String id = criar(corpo("Detalhe", BAIRRO_CENTRO, null)).andReturn().getResponse().getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        enviar(get("/api/v1/pontos/" + id), tokens.bearerAdministrador(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Detalhe"));
        enviar(get("/api/v1/pontos"), tokens.bearerAdministrador(), null)
                .andExpect(jsonPath("$[?(@.nome == 'Detalhe')]", hasSize(1)));
    }

    @Test
    void criarRemoveEspacosDasPontasDosTextos() throws Exception {
        criar("{\"nome\":\"  Com espaços  \",\"bairroId\":\"" + BAIRRO_CENTRO + "\",\"endereco\":\" Rua X \","
                + "\"numeroReferencia\":\"   \",\"dosagemMl\":1,\"distanciaEntreSubpontosMetros\":1,"
                + "\"quantidadeDeSubpontos\":1}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Com espaços"))
                .andExpect(jsonPath("$.endereco").value("Rua X"))
                .andExpect(jsonPath("$.numeroReferencia").value(nullValue()));
    }

    @Test
    void criarComDadosInvalidosRetorna400ENaoGravaNada() throws Exception {
        int antes = contagemDePontos();
        String longo = "x".repeat(151);

        criar("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors", hasSize(6)));
        criar(corpo(longo, BAIRRO_CENTRO, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("nome"));
        criar(corpo("N", BAIRRO_CENTRO, "\"dosagemMl\":0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dosagemMl"));
        criar(corpo("N", BAIRRO_CENTRO, "\"distanciaEntreSubpontosMetros\":-1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("distanciaEntreSubpontosMetros"));
        criar(corpo("N", BAIRRO_CENTRO, "\"quantidadeDeSubpontos\":0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantidadeDeSubpontos"));
        criar(corpo("N", BAIRRO_CENTRO, "\"toleranciaAtrasoDias\":-1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("toleranciaAtrasoDias"));
        criar(corpo("N", BAIRRO_CENTRO, "\"larguraMetros\":0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("larguraMetros"));
        criar(corpo("N", BAIRRO_CENTRO, "\"dosagemMl\":1.234")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dosagemMl"));
        criar("{nao é json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUISICAO_INVALIDA"));

        assertThat(contagemDePontos()).isEqualTo(antes);
    }

    @Test
    void bairroEAplicadorInvalidosOuDeOutroTenantSaoRecusados() throws Exception {
        int antes = contagemDePontos();
        String outroTenant = "outro-" + UUID.randomUUID().toString().substring(0, 8);
        UUID bairroDoOutro = UUID.randomUUID();
        jdbc.update("insert into tenant (id, nome) values (?, 'Outro')", outroTenant);
        jdbc.update("insert into bairro (id, tenant_id, nome) values (?, ?, 'Outro bairro')", bairroDoOutro,
                outroTenant);

        criar(corpo("N", UUID.randomUUID().toString(), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bairroId"));
        criar(corpo("N", "nao-e-uuid", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bairroId"));
        criar(corpo("N", bairroDoOutro.toString(), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bairroId"));
        criar(corpo("N", BAIRRO_CENTRO, "\"aplicadorId\":\"" + UUID.randomUUID() + "\"")).andExpect(
                status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("aplicadorId"));
        criar(corpo("N", BAIRRO_CENTRO, "\"aplicadorId\":\"" + ADMIN + "\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("aplicadorId"));
        criar(corpo("N", BAIRRO_CENTRO, "\"aplicadorId\":\"" + APLICADOR_DESATIVADO + "\""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OPERACAO_NAO_PERMITIDA"));

        assertThat(contagemDePontos()).isEqualTo(antes);
    }

    @Test
    void soAdministradorESubAdministradorCadastram() throws Exception {
        String corpo = corpo("N", BAIRRO_CENTRO, null);
        String sub = tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, "gaspar-sc", "SUB_ADMINISTRADOR");
        enviar(post("/api/v1/pontos"), sub, corpo).andExpect(status().isOk());

        enviar(post("/api/v1/pontos"), tokens.bearerAplicador(), corpo).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        enviar(put("/api/v1/pontos/" + PA_001), tokens.bearerAplicador(), corpo).andExpect(status().isForbidden());
        enviar(post("/api/v1/pontos"), null, corpo).andExpect(status().isUnauthorized());
        enviar(put("/api/v1/pontos/" + PA_001), null, corpo).andExpect(status().isUnauthorized());
    }

    // ---- editar ---------------------------------------------------------------------------------------------

    @Test
    void editarCadastroCompletoEmPontoSemAgendamentoNemExecucao() throws Exception {
        editar(PA_001, corpo("Novo nome", BAIRRO_BELA_VISTA, "\"toleranciaAtrasoDias\":9,\"numeroReferencia\":\"7\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PA_001))
                .andExpect(jsonPath("$.identificador").value("PA-001"))
                .andExpect(jsonPath("$.nome").value("Novo nome"))
                .andExpect(jsonPath("$.bairro").value("Bela Vista"))
                .andExpect(jsonPath("$.endereco").value("Rua Nova"))
                .andExpect(jsonPath("$.quantidadeDeSubpontos").value(8))
                .andExpect(jsonPath("$.toleranciaAtrasoDias").value(9))
                .andExpect(jsonPath("$.estado").value("ENDERECADA"));
        enviar(get("/api/v1/pontos/" + PA_001), tokens.bearerAdministrador(), null)
                .andExpect(jsonPath("$.nome").value("Novo nome")).andExpect(jsonPath("$.bairroId")
                        .value(BAIRRO_BELA_VISTA));
    }

    @Test
    void oCorpoSubstituiOCadastro() throws Exception {
        // PA-001 do seed tem numeroReferencia e descricaoDoTrecho; o corpo sem eles os apaga, e a tolerância volta a 4
        jdbc.update("update ponto set tolerancia_atraso_dias = 9 where id = ?", UUID.fromString(PA_001));
        entityManager.clear();

        editar(PA_001, corpo("Só o mínimo", BAIRRO_CENTRO, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroReferencia").value(nullValue()))
                .andExpect(jsonPath("$.descricaoDoTrecho").value(nullValue()))
                .andExpect(jsonPath("$.toleranciaAtrasoDias").value(4));
    }

    @Test
    void ignoraOAplicadorNaEdicao() throws Exception {
        editar(PA_001, corpo("N", BAIRRO_CENTRO, "\"aplicadorId\":\"" + APLICADOR_3 + "\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicadorId").value(nullValue()))
                .andExpect(jsonPath("$.estado").value("ENDERECADA"));
    }

    @Test
    void comAgendamentoVigenteSoNomeEToleranciaMudam() throws Exception {
        // PA-003: ativa, com agendamento vigente (dosagem 25, distância 5, 6 subpontos, endereço "Travessa da Colina")
        editar(PA_003, corpo("Nome novo", BAIRRO_BELA_VISTA, "\"toleranciaAtrasoDias\":6")).andExpect(
                status().isConflict()).andExpect(jsonPath("$.code").value("OPERACAO_NAO_PERMITIDA"))
                .andExpect(jsonPath("$.detail").isNotEmpty());

        String igualAoCadastro = "{\"nome\":\"Nome novo\",\"bairroId\":\"" + BAIRRO_BELA_VISTA
                + "\",\"endereco\":\"Travessa da Colina\",\"numeroReferencia\":\"45\",\"dosagemMl\":25,"
                + "\"distanciaEntreSubpontosMetros\":5.00,\"quantidadeDeSubpontos\":6,\"toleranciaAtrasoDias\":6}";
        editar(PA_003, igualAoCadastro).andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Nome novo"))
                .andExpect(jsonPath("$.toleranciaAtrasoDias").value(6))
                .andExpect(jsonPath("$.quantidadeDeSubpontos").value(6))
                .andExpect(jsonPath("$.agendamento.datas", hasSize(3)));
    }

    @Test
    void comExecucaoRegistradaOCadastroCompletoNaoMudaMesmoSemAgendamentoVigente() throws Exception {
        ItemDeRegistro item = new ItemDeRegistro(UUID.randomUUID().toString(), UUID.randomUUID().toString(), PA_003,
                "00000000-0000-4000-8000-000000000402", "00000000-0000-4000-8000-000000000501",
                new BigDecimal("-26.931700"), new BigDecimal("-48.958900"),
                OffsetDateTime.of(2026, 10, 12, 14, 30, 0, 0, ZoneOffset.ofHours(-3)));
        assertThat(registro.registrar("gaspar-sc", UUID.fromString(APLICADOR_3), item).status())
                .isEqualTo(StatusItem.CRIADO);
        jdbc.update("update agendamento set vigente = false where ponto_id = ?", UUID.fromString(PA_003));
        entityManager.flush();
        entityManager.clear();

        editar(PA_003, corpo("N", BAIRRO_BELA_VISTA, null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERACAO_NAO_PERMITIDA"));
    }

    @Test
    void pontoInativoSemAgendamentoVigenteNemExecucaoPodeSerEditadoPorCompleto() throws Exception {
        // PA-005: inativa; o único agendamento está encerrado (vigente = false) e não há subpontos
        editar(PA_005, corpo("Rua do Porto 2", BAIRRO_CENTRO, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.endereco").value("Rua Nova"))
                .andExpect(jsonPath("$.estado").value("INATIVA"));
    }

    @Test
    void editarComDadosInvalidosOuPontoDeOutroTenantEhRecusado() throws Exception {
        editar(PA_001, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        editar(PA_001, corpo("N", UUID.randomUUID().toString(), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("bairroId"));
        editar(UUID.randomUUID().toString(), corpo("N", BAIRRO_CENTRO, null)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PONTO_NAO_ENCONTRADO"));
        editar("nao-e-uuid", corpo("N", BAIRRO_CENTRO, null)).andExpect(status().isNotFound());
        String outroTenant = tokens.bearer(TokensDeTeste.ID_ADMINISTRADOR, "outro-sc", "ADMINISTRADOR");
        enviar(put("/api/v1/pontos/" + PA_001), outroTenant, corpo("N", BAIRRO_CENTRO, null))
                .andExpect(status().isNotFound());
    }
}
