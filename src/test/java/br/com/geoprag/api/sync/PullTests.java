package br.com.geoprag.api.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;
import br.com.geoprag.api.sync.dto.ItemDeRegistro;
import br.com.geoprag.api.sync.dto.StatusItem;
import br.com.geoprag.api.sync.service.RegistroAplicacaoService;

/**
 * {@code GET /api/v1/sync/pull} (snapshot) sobre o seed de Gaspar: PA-002 direcionada (aplicador 3), PA-003 ativa (3),
 * PA-004 ativa (4), PA-005 inativa (4), PA-006 desativado (3) e o produto fictício {@code ...501}.
 */
@TesteDeIntegracao
@Transactional
class PullTests {

    private static final String PA_003 = "00000000-0000-4000-8000-000000000203";
    private static final String PA_004 = "00000000-0000-4000-8000-000000000204";
    private static final String APLICADOR_3 = "00000000-0000-4000-8000-000000000003";
    private static final String APLICADOR_4 = "00000000-0000-4000-8000-000000000004";
    private static final String PRODUTO = "00000000-0000-4000-8000-000000000501";
    private static final String DATA_PA_003 = "00000000-0000-4000-8000-000000000402";
    private static final String URL = "/api/v1/sync/pull";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private RegistroAplicacaoService registro;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private ResultActions puxar(String authorization) throws Exception {
        var requisicao = get(URL);
        if (authorization != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(requisicao);
    }

    private String aplicador(String id) {
        return tokens.bearer(id, "gaspar-sc", "APLICADOR");
    }

    @Test
    void aplicadorRecebeSoOsSeusPontosAtivosComAgendamentoEProdutos() throws Exception {
        puxar(aplicador(APLICADOR_3)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].id").value(PA_003))
                .andExpect(jsonPath("$.pontos[0].identificador").value("PA-003"))
                .andExpect(jsonPath("$.pontos[0].estado").value("ATIVA"))
                .andExpect(jsonPath("$.pontos[0].aplicadorId").value(APLICADOR_3))
                .andExpect(jsonPath("$.pontos[0].bairro").value("Bela Vista"))
                .andExpect(jsonPath("$.pontos[0].quantidadeDeSubpontos").value(6))
                .andExpect(jsonPath("$.pontos[0].agendamento.datas", hasSize(3)))
                .andExpect(jsonPath("$.pontos[0].agendamento.datas[1].id").value(DATA_PA_003))
                .andExpect(jsonPath("$.pontos[0].agendamento.datas[*].status",
                        contains("CONCLUIDA", "PENDENTE", "PENDENTE")))
                .andExpect(jsonPath("$.pontos[0].subpontos", hasSize(0)))
                .andExpect(jsonPath("$.produtos", hasSize(1)))
                .andExpect(jsonPath("$.produtos[0].id").value(PRODUTO))
                .andExpect(jsonPath("$.produtos[0].nome").value("Larvicida (exemplo)"))
                // snapshot completo: traz o cursor para o app iniciar o incremental (GEOPRAG-205), com removidos vazio
                .andExpect(jsonPath("$.cursor").isNotEmpty())
                .andExpect(jsonPath("$.removidos", hasSize(0)));
    }

    @Test
    void outroAplicadorNaoRecebeOsPontosDeOutroNemOsNaoAtivos() throws Exception {
        // aplicador 4: PA-004 (ativa) sim; PA-005 (inativa, também dele) não
        puxar(aplicador(APLICADOR_4)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].id").value(PA_004))
                .andExpect(jsonPath("$.pontos[0].aplicadorId").value(APLICADOR_4));
    }

    @Test
    void pontoReatribuidoDeixaDeAparecerParaOAplicadorAnterior() throws Exception {
        jdbc.update("update ponto set aplicador_id = ? where id = ?", UUID.fromString(APLICADOR_4),
                UUID.fromString(PA_003));
        entityManager.clear();

        puxar(aplicador(APLICADOR_3)).andExpect(jsonPath("$.pontos", hasSize(0)))
                .andExpect(jsonPath("$.produtos", hasSize(1)));
        puxar(aplicador(APLICADOR_4)).andExpect(jsonPath("$.pontos", hasSize(2)))
                .andExpect(jsonPath("$.pontos[*].identificador", contains("PA-003", "PA-004")));
    }

    @Test
    void pontoQueSaiDeAtivaDeixaDeAparecer() throws Exception {
        jdbc.update("update ponto set estado = 'INATIVA', estado_anterior = 'ATIVA' where id = ?",
                UUID.fromString(PA_003));
        entityManager.clear();

        puxar(aplicador(APLICADOR_3)).andExpect(jsonPath("$.pontos", hasSize(0)));
    }

    @Test
    void aplicadorDeOutroTenantNaoRecebeNadaDeGaspar() throws Exception {
        String outroTenant = tokens.bearer(APLICADOR_3, "outro-sc", "APLICADOR");

        puxar(outroTenant).andExpect(status().isOk())
                .andExpect(jsonPath("$.pontos", hasSize(0)))
                .andExpect(jsonPath("$.produtos", hasSize(0)));
    }

    @Test
    void soOAplicadorBaixa() throws Exception {
        puxar(tokens.bearerAdministrador()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
        puxar(tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, "gaspar-sc", "SUB_ADMINISTRADOR"))
                .andExpect(status().isForbidden());
        puxar(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
    }

    @Test
    void subpontosJaRegistradosVoltamSemRevelarOConflito() throws Exception {
        // o aplicador 4 registra em ponto do aplicador 3: aceito e marcado como conflito (PONTO_REATRIBUIDO)
        ItemDeRegistro item = new ItemDeRegistro(UUID.randomUUID().toString(), UUID.randomUUID().toString(), PA_003,
                DATA_PA_003, PRODUTO, new BigDecimal("-26.931700"), new BigDecimal("-48.958900"),
                OffsetDateTime.of(2026, 10, 12, 14, 30, 0, 0, ZoneOffset.ofHours(-3)));
        assertThat(registro.registrar("gaspar-sc", UUID.fromString(APLICADOR_4), item).status())
                .isEqualTo(StatusItem.CRIADO);
        entityManager.flush();
        entityManager.clear();

        String corpo = puxar(aplicador(APLICADOR_3)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pontos[0].subpontos", hasSize(1)))
                .andExpect(jsonPath("$.pontos[0].subpontos[0].id").value(item.id()))
                .andExpect(jsonPath("$.pontos[0].subpontos[0].latitude").value(-26.9317))
                .andExpect(jsonPath("$.pontos[0].subpontos[0].longitude").value(-48.9589))
                .andExpect(jsonPath("$.pontos[0].subpontos[0].realizadoEm").isNotEmpty())
                .andExpect(jsonPath("$.pontos[0].subpontos[0].recebidoEm").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContainIgnoringCase("conflito").doesNotContain("PONTO_REATRIBUIDO");
        // o portal continua vendo a marca
        mockMvc.perform(get("/api/v1/pontos/" + PA_003).header(HttpHeaders.AUTHORIZATION,
                tokens.bearerAdministrador())).andExpect(jsonPath("$.subpontos[0].conflito").value(true))
                .andExpect(jsonPath("$.subpontos[0].motivoConflito").value("PONTO_REATRIBUIDO"));
    }

    @Test
    void respostaNaoTrazDadosPessoaisNemDeOutroTenant() throws Exception {
        puxar(aplicador(APLICADOR_3)).andExpect(content().string(not(containsString("@"))))
                .andExpect(content().string(not(containsString("senha"))))
                .andExpect(content().string(not(containsString("cpf"))));
    }
}
