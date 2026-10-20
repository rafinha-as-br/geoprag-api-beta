package br.com.geoprag.api.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;

/** A margem de segurança do cursor (padrão 5 s): o cursor recua em relação ao instante do servidor. */
@TesteDeIntegracao
class PullCursorMargemTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;

    @Test
    void oCursorRecuaAMargemPadraoDeCincoSegundos() throws Exception {
        OffsetDateTime antes = OffsetDateTime.now();
        String corpo = mockMvc.perform(get("/api/v1/sync/pull").header(HttpHeaders.AUTHORIZATION,
                tokens.bearerAplicador())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        OffsetDateTime depois = OffsetDateTime.now();

        OffsetDateTime cursor = OffsetDateTime.parse(JsonPath.<String>read(corpo, "$.cursor"));

        assertThat(cursor).isAfterOrEqualTo(antes.minusSeconds(5).minusNanos(1_000))
                .isBeforeOrEqualTo(depois.minusSeconds(5));
    }
}
