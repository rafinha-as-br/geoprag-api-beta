package br.com.geoprag.api.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.geoprag.api.TesteDeIntegracao;

@TesteDeIntegracao
@WithMockUser(roles = "ADMINISTRADOR")
class ErroPadronizadoTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void erroDeValidacaoRetornaFormatoPadraoComCodeEstavelEErrosPorCampo() throws Exception {
        ResultActions resposta = mockMvc.perform(post("/teste/validacao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\",\"idade\":0}"));

        resposta.andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.title").value("Dados inválidos"))
                .andExpect(jsonPath("$.detail").value(
                        "Há campos inválidos. Corrija os dados informados e tente novamente."))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='nome')].message").value("não deve estar em branco"))
                .andExpect(jsonPath("$.errors[?(@.field=='idade')].message").exists());
    }

    @Test
    void erroDeValidacaoEmParametroTambemRetornaValidacaoInvalidaComErrosPorCampo() throws Exception {
        mockMvc.perform(get("/teste/parametro").param("pagina", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("pagina"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void jsonIlegivelRetornaRequisicaoInvalida() throws Exception {
        mockMvc.perform(post("/teste/validacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{nome:"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REQUISICAO_INVALIDA"))
                .andExpect(jsonPath("$.detail").value(
                        "Não foi possível processar a requisição. Verifique os dados enviados e tente novamente."));
    }

    @Test
    void rotaInexistenteRetornaRecursoNaoEncontrado() throws Exception {
        mockMvc.perform(get("/rota-que-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void metodoNaoPermitidoRetornaCodeProprio() throws Exception {
        mockMvc.perform(get("/teste/validacao"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METODO_NAO_PERMITIDO"));
    }

    @Test
    void formatoNaoSuportadoRetornaCodeProprio() throws Exception {
        mockMvc.perform(post("/teste/validacao")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("texto"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FORMATO_NAO_SUPORTADO"));
    }

    @Test
    void erroInesperadoRetornaErroInternoSemVazarDetalhes() throws Exception {
        mockMvc.perform(get("/teste/erro-inesperado"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("ERRO_INTERNO"))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente em instantes."))
                .andExpect(content().string(not(containsString("detalhe interno sensível"))));
    }
}
