package br.com.geoprag.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.geoprag.api.TesteDeIntegracao;

/** {@code POST /api/v1/auth/login} com o contexto completo (perfil test: seed com a senha local fictícia). */
@TesteDeIntegracao
@ExtendWith(OutputCaptureExtension.class)
class LoginTests {

    private static final String SENHA_SEED = "Geoprag@Local2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JwtDecoder decoder;

    private ResultActions login(String identificador, String senha) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Credenciais(identificador, senha))));
    }

    private record Credenciais(String identificador, String senha) {
    }

    @Test
    void loginPorEmailDevolveTokenDeQuinzeMinutosEUsuarioSemDadosSigilosos() throws Exception {
        String corpo = login("  Admin@Example.com ", SENHA_SEED)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.usuario.id").value(TokensDeTeste.ID_ADMINISTRADOR))
                .andExpect(jsonPath("$.usuario.nome").value("Administrador Exemplo"))
                .andExpect(jsonPath("$.usuario.perfil").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.usuario.tenantId").value("gaspar-sc"))
                .andExpect(jsonPath("$.usuario.email").value("admin@example.com"))
                .andExpect(jsonPath("$.usuario.status").value("ATIVO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo).doesNotContain("11111111111").doesNotContain("senha").doesNotContain("$2a$");
    }

    @Test
    void loginPorCpfComOuSemMascara() throws Exception {
        login("111.111.111-11", SENHA_SEED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(TokensDeTeste.ID_ADMINISTRADOR));
        login("33333333333", SENHA_SEED)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.perfil").value("APLICADOR"));
    }

    @Test
    void tokenEmitidoTemSubTenantEPerfilEVale15Minutos() throws Exception {
        String corpo = login("aplicador1@example.com", SENHA_SEED).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode resposta = json.readTree(corpo);

        Jwt jwt = decoder.decode(resposta.get("accessToken").asText());

        assertThat(jwt.getSubject()).isEqualTo(TokensDeTeste.ID_APLICADOR);
        assertThat(jwt.getClaimAsString("tenantId")).isEqualTo("gaspar-sc");
        assertThat(jwt.getClaimAsString("perfil")).isEqualTo("APLICADOR");
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(900);
        assertThat(jwt.getClaims()).doesNotContainKeys("cpf", "email", "nome", "senha");
    }

    @Test
    void senhaErradaEIdentificadorInexistenteRespondemIgualSemRevelarQualDosDoisFalhou() throws Exception {
        String senhaErrada = login("admin@example.com", "senha-errada").andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn().getResponse().getContentAsString();
        String inexistente = login("ninguem@example.com", "senha-errada").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(inexistente).isEqualTo(senhaErrada);
    }

    @Test
    void senhaLongaDemaisParaOBcryptNaoViraErroInterno() throws Exception {
        login("admin@example.com", "x".repeat(150)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
        login("ninguem@example.com", "x".repeat(150)).andExpect(status().isUnauthorized());
    }

    @Test
    void campoAcimaDoLimiteRetornaValidacaoInvalida() throws Exception {
        login("a".repeat(300) + "@example.com", SENHA_SEED).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        login("admin@example.com", "x".repeat(201)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("senha"));
    }

    @Test
    void cpfInexistenteOuMalFormadoRetornaCredenciaisInvalidas() throws Exception {
        login("99999999999", SENHA_SEED).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
        login("123", SENHA_SEED).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void usuarioDesativadoComSenhaCorretaRecebe403() throws Exception {
        login("aplicador.desativado@example.com", SENHA_SEED).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("USUARIO_DESATIVADO"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void usuarioDesativadoComSenhaErradaNaoRevelaQueEstaDesativado() throws Exception {
        login("aplicador.desativado@example.com", "senha-errada").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"));
    }

    @Test
    void camposAusentesOuEmBrancoRetornamValidacaoInvalidaComErrosPorCampo() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.field=='identificador')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='senha')]").exists());
        login("   ", SENHA_SEED).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("identificador"));
    }

    @Test
    void getNoLoginExigeAutenticacaoPoisSoOPostEPublico() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
    }

    @Test
    void cpfNemSenhaApareceNosLogsEmLoginComSucessoOuFalha(CapturedOutput saida) throws Exception {
        login("111.111.111-11", SENHA_SEED).andExpect(status().isOk());
        login("111.111.111-11", "senha-errada").andExpect(status().isUnauthorized());
        login("999.999.999-99", "senha-errada").andExpect(status().isUnauthorized());
        login("aplicador.desativado@example.com", SENHA_SEED).andExpect(status().isForbidden());

        assertThat(saida.getAll())
                .doesNotContain("11111111111").doesNotContain("111.111.111-11")
                .doesNotContain("99999999999").doesNotContain("999.999.999-99")
                .doesNotContain(SENHA_SEED).doesNotContain("senha-errada")
                .doesNotContain("$2a$");
    }

    @Test
    void toStringDoPedidoNaoExpoeIdentificadorNemSenha() {
        String texto = new br.com.geoprag.api.auth.dto.LoginRequest("111.111.111-11", SENHA_SEED).toString();

        assertThat(texto).doesNotContain("111").doesNotContain(SENHA_SEED);
    }
}
