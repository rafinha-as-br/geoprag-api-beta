package br.com.geoprag.api.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import br.com.geoprag.api.TesteDeIntegracao;

@TesteDeIntegracao
class SwaggerESaudeTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointDeSaudeResponde() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @WithMockUser(roles = "ADMINISTRADOR")
    void apenasASaudeEstaExpostaNoActuator() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiListaOEndpointDeSaude() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/actuator/health']").exists());
    }

    @Test
    void swaggerUiEstaDisponivel() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
