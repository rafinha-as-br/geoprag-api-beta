package br.com.geoprag.api.common.cors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import br.com.geoprag.api.TesteDeIntegracao;

/** O perfil de teste libera apenas {@code http://localhost:3000} (simula o portal Web local). */
@TesteDeIntegracao
class CorsTests {

    private static final String ORIGEM_PERMITIDA = "http://localhost:3000";
    private static final String ORIGEM_NEGADA = "http://outra-origem.example";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightDeOrigemPermitidaERespondidoComOsCabecalhosCors() throws Exception {
        mockMvc.perform(options("/teste/validacao")
                        .header(HttpHeaders.ORIGIN, ORIGEM_PERMITIDA)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_PERMITIDA))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsString("authorization")));
    }

    @Test
    void preflightDeOrigemNaoPermitidaERejeitado() throws Exception {
        mockMvc.perform(options("/teste/validacao")
                        .header(HttpHeaders.ORIGIN, ORIGEM_NEGADA)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void requisicaoDeOrigemPermitidaRecebeOCabecalhoDeOrigem() throws Exception {
        mockMvc.perform(post("/teste/validacao")
                        .header(HttpHeaders.ORIGIN, ORIGEM_PERMITIDA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Fulano\",\"idade\":30}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_PERMITIDA));
    }
}
