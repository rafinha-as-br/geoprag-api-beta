package br.com.geoprag.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.geoprag.api.TesteDeIntegracao;

/** Filtro de segurança, {@code GET /api/v1/auth/me}, autorização por papel e isolamento de tenant. */
@TesteDeIntegracao
class AutorizacaoTests {

    private static final String TENANT = "gaspar-sc";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;

    private ResultActions chamar(String caminho, String authorization) throws Exception {
        var requisicao = get(caminho);
        if (authorization != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(requisicao);
    }

    private static void naoAutenticado(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"))
                .andExpect(jsonPath("$.status").value(401));
    }

    // ---- /auth/me -----------------------------------------------------------------------------------------

    @Test
    void meDevolveOUsuarioDoTokenParaQualquerPerfil() throws Exception {
        chamar("/api/v1/auth/me", tokens.bearerAdministrador()).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TokensDeTeste.ID_ADMINISTRADOR))
                .andExpect(jsonPath("$.perfil").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.tenantId").value(TENANT))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.cpf").doesNotExist())
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
        chamar("/api/v1/auth/me", tokens.bearerAplicador()).andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("APLICADOR"));
        chamar("/api/v1/auth/me", tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, TENANT, "SUB_ADMINISTRADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil").value("SUB_ADMINISTRADOR"));
    }

    @Test
    void meSemTokenRetornaNaoAutenticado() throws Exception {
        naoAutenticado(chamar("/api/v1/auth/me", null));
    }

    @Test
    void tokenAdulteradoOuLixoRetornaNaoAutenticado() throws Exception {
        String valido = tokens.bearerAdministrador();

        naoAutenticado(chamar("/api/v1/auth/me", valido.substring(0, valido.length() - 3) + "abc"));
        naoAutenticado(chamar("/api/v1/auth/me", "Bearer isto-nao-e-um-jwt"));
        naoAutenticado(chamar("/api/v1/auth/me", "Basic dXNlcjpwYXNz"));
    }

    @Test
    void tokenExpiradoRetornaNaoAutenticado() throws Exception {
        String expirado = tokens.token(TokensDeTeste.ID_ADMINISTRADOR, TENANT, "ADMINISTRADOR",
                Instant.now().minus(Duration.ofHours(1)), Duration.ofMinutes(15));

        naoAutenticado(chamar("/api/v1/auth/me", "Bearer " + expirado));
    }

    @Test
    void tokenAssinadoComOutroSegredoRetornaNaoAutenticado() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(TokensDeTeste.ID_ADMINISTRADOR)
                .claim("tenantId", TENANT).claim("perfil", "ADMINISTRADOR")
                .issueTime(new java.util.Date())
                .expirationTime(java.util.Date.from(Instant.now().plus(Duration.ofMinutes(15))))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner("outro-segredo-fictício-com-mais-de-32-bytes!!".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        naoAutenticado(chamar("/api/v1/auth/me", "Bearer " + jwt.serialize()));
    }

    @Test
    void tokenSemTenantOuComPerfilDesconhecidoRetornaNaoAutenticado() throws Exception {
        naoAutenticado(chamar("/api/v1/auth/me", tokens.bearer(TokensDeTeste.ID_ADMINISTRADOR, null, "ADMINISTRADOR")));
        naoAutenticado(chamar("/api/v1/auth/me", tokens.bearer(TokensDeTeste.ID_ADMINISTRADOR, TENANT, null)));
        naoAutenticado(chamar("/api/v1/auth/me", tokens.bearer(TokensDeTeste.ID_ADMINISTRADOR, TENANT, "ROOT")));
    }

    @Test
    void tokenDeUmTenantNaoLeUsuarioDeOutro() throws Exception {
        // Token válido, mas o tenant do token não é o do usuário do "sub": é como se o usuário não existisse.
        naoAutenticado(chamar("/api/v1/auth/me", tokens.bearer(TokensDeTeste.ID_ADMINISTRADOR, "outro-sc", "ADMINISTRADOR")));
    }

    @Test
    void subQueNaoEhUuidOuNaoExisteRetornaNaoAutenticado() throws Exception {
        naoAutenticado(chamar("/api/v1/auth/me", tokens.bearer("nao-e-uuid", TENANT, "ADMINISTRADOR")));
        naoAutenticado(chamar("/api/v1/auth/me",
                tokens.bearer("00000000-0000-4000-8000-0000000000ff", TENANT, "ADMINISTRADOR")));
    }

    @Test
    void usuarioDesativadoDepoisDeReceberOTokenPerdeOAcesso() throws Exception {
        chamar("/api/v1/auth/me", tokens.bearer(TokensDeTeste.ID_APLICADOR_DESATIVADO, TENANT, "APLICADOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USUARIO_DESATIVADO"));
    }

    // ---- autorização por papel ----------------------------------------------------------------------------

    @Test
    void administradorESubAdministradorAcessamAAdministracao() throws Exception {
        chamar("/teste/administracao", tokens.bearerAdministrador()).andExpect(status().isOk());
        chamar("/teste/administracao",
                tokens.bearer(TokensDeTeste.ID_SUB_ADMINISTRADOR, TENANT, "SUB_ADMINISTRADOR"))
                .andExpect(status().isOk());
    }

    @Test
    void aplicadorNaoAcessaAAdministracao() throws Exception {
        chamar("/teste/administracao", tokens.bearerAplicador()).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void administradorNaoAcessaORecursoDoAplicador() throws Exception {
        chamar("/teste/aplicador", tokens.bearerAplicador()).andExpect(status().isOk());
        chamar("/teste/aplicador", tokens.bearerAdministrador()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACESSO_NEGADO"));
    }

    @Test
    void recursoProtegidoSemTokenRetornaNaoAutenticadoEnaoAcessoNegado() throws Exception {
        naoAutenticado(chamar("/teste/administracao", null));
    }

    // ---- o que continua público ---------------------------------------------------------------------------

    @Test
    void saudeEDocumentacaoContinuamPublicas() throws Exception {
        chamar("/actuator/health", null).andExpect(status().isOk());
        chamar("/v3/api-docs", null).andExpect(status().isOk());
        chamar("/swagger-ui/index.html", null).andExpect(status().isOk());
    }

    @Test
    void rotaInexistenteExigeAutenticacaoPrimeiro() throws Exception {
        naoAutenticado(chamar("/api/v1/rota-que-nao-existe", null));
        chamar("/api/v1/rota-que-nao-existe", tokens.bearerAdministrador()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECURSO_NAO_ENCONTRADO"));
    }
}
