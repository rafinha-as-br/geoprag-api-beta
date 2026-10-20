package br.com.geoprag.api.ponto;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;
import br.com.geoprag.api.auth.domain.Tenant;
import br.com.geoprag.api.auth.repository.TenantRepository;
import br.com.geoprag.api.ponto.domain.Bairro;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.repository.BairroRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;
import br.com.geoprag.api.sync.dto.ItemDeRegistro;
import br.com.geoprag.api.sync.service.RegistroAplicacaoService;

/** {@code GET /pontos}, {@code /pontos/{id}}, {@code /bairros} e {@code /aplicadores} sobre o seed de Gaspar. */
@TesteDeIntegracao
@Transactional
class LeituraTests {

    private static final String GASPAR = "gaspar-sc";
    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final UUID APLICADOR_4 = UUID.fromString("00000000-0000-4000-8000-000000000004");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private TenantRepository tenants;
    @Autowired
    private BairroRepository bairros;
    @Autowired
    private PontoRepository pontos;
    @Autowired
    private RegistroAplicacaoService registro;
    @Autowired
    private EntityManager entityManager;

    private ResultActions chamar(String caminho, String authorization) throws Exception {
        var requisicao = get(caminho);
        if (authorization != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(requisicao);
    }

    private ResultActions admin(String caminho) throws Exception {
        return chamar(caminho, tokens.bearerAdministrador());
    }

    // ---- GET /pontos -----------------------------------------------------------------------------------------

    @Test
    void listaOsSeisPontosDoSeedEmOrdemDeIdentificador() throws Exception {
        admin("/api/v1/pontos").andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].identificador", contains("PA-001", "PA-002", "PA-003", "PA-004", "PA-005",
                        "PA-006")));
    }

    @Test
    void cadaPontoTemOModeloDoContratoComAgendamentoEDatas() throws Exception {
        admin("/api/v1/pontos").andExpect(status().isOk())
                // PA-003: ativo, com agendamento vigente e uma data concluída e duas pendentes
                .andExpect(jsonPath("$[2].id").value(PA_003))
                .andExpect(jsonPath("$[2].nome").value("Travessa da Colina"))
                .andExpect(jsonPath("$[2].bairro").value("Bela Vista"))
                .andExpect(jsonPath("$[2].bairroId").value("00000000-0000-4000-8000-000000000102"))
                .andExpect(jsonPath("$[2].endereco").value("Travessa da Colina"))
                .andExpect(jsonPath("$[2].numeroReferencia").value("45"))
                .andExpect(jsonPath("$[2].estado").value("ATIVA"))
                .andExpect(jsonPath("$[2].estadoAnterior").value("DIRECIONADA"))
                .andExpect(jsonPath("$[2].aplicadorId").value(TokensDeTeste.ID_APLICADOR))
                .andExpect(jsonPath("$[2].quantidadeDeSubpontos").value(6))
                .andExpect(jsonPath("$[2].toleranciaAtrasoDias").value(4))
                .andExpect(jsonPath("$[2].dosagemMl").value(25.0))
                .andExpect(jsonPath("$[2].distanciaEntreSubpontosMetros").value(5.0))
                .andExpect(jsonPath("$[2].agendamento.dataInicio").value("2026-10-05"))
                .andExpect(jsonPath("$[2].agendamento.intervaloDias").value(7))
                .andExpect(jsonPath("$[2].agendamento.quantidadeRecorrencias").value(3))
                .andExpect(jsonPath("$[2].agendamento.datas", hasSize(3)))
                .andExpect(jsonPath("$[2].agendamento.datas[*].data", contains("2026-10-05", "2026-10-12",
                        "2026-10-19")))
                .andExpect(jsonPath("$[2].agendamento.datas[*].status", contains("CONCLUIDA", "PENDENTE",
                        "PENDENTE")))
                .andExpect(jsonPath("$[2].agendamento.datas[1].id").value("00000000-0000-4000-8000-000000000402"))
                .andExpect(jsonPath("$[2].subpontos", hasSize(0)))
                // PA-001: sem aplicador, sem agendamento, medidas opcionais ausentes
                .andExpect(jsonPath("$[0].estado").value("ENDERECADA"))
                .andExpect(jsonPath("$[0].aplicadorId", nullValue()))
                .andExpect(jsonPath("$[0].agendamento", nullValue()))
                .andExpect(jsonPath("$[0].larguraMetros", nullValue()))
                .andExpect(jsonPath("$[0].estadoAnterior", nullValue()))
                // PA-002: medidas informadas
                .andExpect(jsonPath("$[1].larguraMetros").value(1.5))
                .andExpect(jsonPath("$[1].velocidadeMetrosPorSegundo").value(0.8));
    }

    @Test
    void pontoInativoMantemOHistoricoDoUltimoAgendamento() throws Exception {
        admin("/api/v1/pontos").andExpect(status().isOk())
                .andExpect(jsonPath("$[4].identificador").value("PA-005"))
                .andExpect(jsonPath("$[4].estado").value("INATIVA"))
                .andExpect(jsonPath("$[4].agendamento.dataInicio").value("2026-09-01"))
                .andExpect(jsonPath("$[4].agendamento.datas[*].status", contains("CONCLUIDA", "CANCELADA")));
    }

    @Test
    void naoExpoeTenantNemDadosSigilosos() throws Exception {
        String corpo = admin("/api/v1/pontos").andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(corpo).doesNotContain("tenant").doesNotContain("cpf")
                .doesNotContain("senha").doesNotContain("vazao");
    }

    @Test
    void filtraPorEstado() throws Exception {
        admin("/api/v1/pontos?estado=ATIVA").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].identificador", contains("PA-003", "PA-004")));
        admin("/api/v1/pontos?estado=DESATIVADO").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].identificador", contains("PA-006")));
    }

    @Test
    void filtraPorBairroSemDiferenciarMaiusculasEPorAmbos() throws Exception {
        admin("/api/v1/pontos?bairro=centro").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].identificador", contains("PA-001", "PA-002")));
        admin("/api/v1/pontos?bairro=Margem do Rio&estado=INATIVA").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].identificador", contains("PA-005")));
        admin("/api/v1/pontos?bairro=Inexistente").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        admin("/api/v1/pontos?bairro=").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    void estadoInvalidoNoFiltroRetornaRequisicaoInvalida() throws Exception {
        admin("/api/v1/pontos?estado=NAO_EXISTE").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REQUISICAO_INVALIDA"));
    }

    // ---- GET /pontos/{id} ------------------------------------------------------------------------------------

    @Test
    void detalhaUmPontoComAgendamentoEDatas() throws Exception {
        admin("/api/v1/pontos/" + PA_003).andExpect(status().isOk())
                .andExpect(jsonPath("$.identificador").value("PA-003"))
                .andExpect(jsonPath("$.agendamento.datas", hasSize(3)))
                .andExpect(jsonPath("$.subpontos", hasSize(0)));
    }

    @Test
    void detalheMostraOsRegistrosComAMarcaDeConflitoParaOGestor() throws Exception {
        // O aplicador 4 registra num ponto do aplicador 3: aceito e marcado como conflito.
        String id = UUID.randomUUID().toString();
        registro.registrar(GASPAR, APLICADOR_4, new ItemDeRegistro(id, UUID.randomUUID().toString(), PA_003,
                "00000000-0000-4000-8000-000000000402", "00000000-0000-4000-8000-000000000501",
                new BigDecimal("-26.9317"), new BigDecimal("-48.9589"),
                OffsetDateTime.of(2026, 10, 12, 14, 30, 0, 0, ZoneOffset.ofHours(-3))));
        entityManager.flush();

        admin("/api/v1/pontos/" + PA_003).andExpect(status().isOk())
                .andExpect(jsonPath("$.subpontos", hasSize(1)))
                .andExpect(jsonPath("$.subpontos[0].id").value(id))
                .andExpect(jsonPath("$.subpontos[0].latitude").value(-26.9317))
                .andExpect(jsonPath("$.subpontos[0].realizadoEm").value("2026-10-12T17:30:00Z"))
                .andExpect(jsonPath("$.subpontos[0].recebidoEm", not(nullValue())))
                .andExpect(jsonPath("$.subpontos[0].conflito").value(true))
                .andExpect(jsonPath("$.subpontos[0].motivoConflito").value("PONTO_REATRIBUIDO"));
        admin("/api/v1/pontos?estado=ATIVA").andExpect(jsonPath("$[0].subpontos", hasSize(1)));
    }

    @Test
    void idInexistenteOuMalformadoRetorna404PontoNaoEncontrado() throws Exception {
        admin("/api/v1/pontos/" + UUID.randomUUID()).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PONTO_NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.status").value(404));
        admin("/api/v1/pontos/nao-e-uuid").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PONTO_NAO_ENCONTRADO"));
    }

    // ---- isolamento por tenant -----------------------------------------------------------------------------------

    @Test
    void pontoDeOutroTenantRetorna404EListaNaoMisturaTenants() throws Exception {
        Tenant outro = tenants.saveAndFlush(new Tenant("outro-sc", "Outro (SC)"));
        Bairro bairro = bairros.save(new Bairro(outro, "Centro"));
        Ponto pontoOutro = pontos.save(new Ponto(outro, "PA-001", "Ponto de outro tenant", bairro, "Rua X",
                new BigDecimal("10"), new BigDecimal("5"), 3, null));
        entityManager.flush();
        String tokenOutro = tokens.bearer(TokensDeTeste.ID_ADMINISTRADOR, "outro-sc", "ADMINISTRADOR");

        // com o token de Gaspar, o ponto de outro tenant "não existe" (404, e não 403)
        admin("/api/v1/pontos/" + pontoOutro.getId()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PONTO_NAO_ENCONTRADO"));
        admin("/api/v1/pontos").andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[*].nome", not(org.hamcrest.Matchers.hasItem("Ponto de outro tenant"))));
        // com o token do outro tenant, só os dele; e o PA-003 de Gaspar não existe
        chamar("/api/v1/pontos", tokenOutro).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pontoOutro.getId().toString()));
        chamar("/api/v1/pontos/" + PA_003, tokenOutro).andExpect(status().isNotFound());
        chamar("/api/v1/bairros", tokenOutro).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Centro"));
        chamar("/api/v1/aplicadores", tokenOutro).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---- GET /bairros ----------------------------------------------------------------------------------------

    @Test
    void listaOsBairrosDoTenantEmOrdemAlfabetica() throws Exception {
        admin("/api/v1/bairros").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", contains("Bela Vista", "Centro", "Margem do Rio")))
                .andExpect(jsonPath("$[0].id").value("00000000-0000-4000-8000-000000000102"))
                .andExpect(jsonPath("$[0].*", hasSize(2)));
    }

    // ---- GET /aplicadores ------------------------------------------------------------------------------------

    @Test
    void listaSoOsAplicadoresSemDadosPessoais() throws Exception {
        String corpo = admin("/api/v1/aplicadores").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].nome", contains("Aplicador Exemplo Desativado", "Aplicador Exemplo Dois",
                        "Aplicador Exemplo Um")))
                .andExpect(jsonPath("$[*].status", containsInAnyOrder("ATIVO", "ATIVO", "DESATIVADO")))
                .andExpect(jsonPath("$[0].*", hasSize(3)))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(corpo).doesNotContain("@example.com").doesNotContain("cpf")
                .doesNotContain("11111111111").doesNotContain("Administrador").doesNotContain("$2a$");
    }

    @Test
    void filtraAplicadoresPorStatus() throws Exception {
        admin("/api/v1/aplicadores?status=ATIVO").andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status", contains("ATIVO", "ATIVO")));
        admin("/api/v1/aplicadores?status=DESATIVADO").andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Aplicador Exemplo Desativado"));
        admin("/api/v1/aplicadores?status=TALVEZ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUISICAO_INVALIDA"));
    }

    // ---- autorização -----------------------------------------------------------------------------------------

    @Test
    void subAdministradorTambemAcessa() throws Exception {
        String sub = tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, GASPAR, "SUB_ADMINISTRADOR");
        for (String caminho : new String[] {"/api/v1/pontos", "/api/v1/pontos/" + PA_003, "/api/v1/bairros",
                "/api/v1/aplicadores"}) {
            chamar(caminho, sub).andExpect(status().isOk());
        }
    }

    @Test
    void aplicadorNaoAcessaOsEndpointsDeAdministracao() throws Exception {
        for (String caminho : new String[] {"/api/v1/pontos", "/api/v1/pontos/" + PA_003, "/api/v1/bairros",
                "/api/v1/aplicadores"}) {
            chamar(caminho, tokens.bearerAplicador()).andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"))
                    .andExpect(content().string(not(containsString("PA-003"))));
        }
    }

    @Test
    void semTokenRetorna401() throws Exception {
        for (String caminho : new String[] {"/api/v1/pontos", "/api/v1/pontos/" + PA_003, "/api/v1/bairros",
                "/api/v1/aplicadores"}) {
            chamar(caminho, null).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
        }
    }
}
