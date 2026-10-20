package br.com.geoprag.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;

/** {@code POST /api/v1/auth/refresh} e a entrega do refresh token no login do aplicador. */
@TesteDeIntegracao
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class RefreshTokenTests {

    private static final String SENHA_SEED = "Geoprag@Local2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private JwtDecoder decoder;

    private record Credenciais(String identificador, String senha) {
    }

    private record PedidoRefresh(String refreshToken) {
    }

    private JsonNode login(String identificador) throws Exception {
        String corpo = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credenciais(identificador, SENHA_SEED))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new PedidoRefresh(refreshToken))));
    }

    private JsonNode refreshOk(String refreshToken) throws Exception {
        return json.readTree(refresh(refreshToken).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static void refreshInvalido(ResultActions resposta) throws Exception {
        resposta.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("REFRESH_INVALIDO"))
                .andExpect(jsonPath("$.status").value(401));
    }

    private static String sha256(String valor) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(valor.getBytes(StandardCharsets.UTF_8)));
    }

    private int linhasComHash(String hash) {
        entityManager.flush();
        return jdbc.queryForObject("select count(*) from refresh_token where token_hash = ?", Integer.class, hash);
    }

    // ---- login ---------------------------------------------------------------------------------------------

    @Test
    void loginDoAplicadorEntregaRefreshOpacoQueNaoEUmJwt() throws Exception {
        JsonNode resposta = login("aplicador1@example.com");

        String refresh = resposta.get("refreshToken").asText();
        assertThat(refresh).hasSize(43).matches("[A-Za-z0-9_-]+").doesNotContain(".");
        assertThat(resposta.get("accessToken").asText()).contains(".");
        assertThat(resposta.get("usuario").get("perfil").asText()).isEqualTo("APLICADOR");
    }

    @Test
    void loginDeAdministradorENaoAplicadorNaoRecebeRefresh() throws Exception {
        assertThat(login("admin@example.com").has("refreshToken")).isFalse();
        assertThat(login("subadmin@example.com").has("refreshToken")).isFalse();
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where usuario_id in (?, ?)", Integer.class,
                java.util.UUID.fromString(TokensDeTeste.ID_ADMINISTRADOR),
                java.util.UUID.fromString(TokensDeTeste.ID_SUB_ADMINISTRADOR))).isZero();
    }

    @Test
    void cadaLoginGeraUmRefreshDiferenteEAmbosValem() throws Exception {
        String primeiro = login("aplicador1@example.com").get("refreshToken").asText();
        String segundo = login("aplicador1@example.com").get("refreshToken").asText();

        assertThat(primeiro).isNotEqualTo(segundo);
        refreshOk(primeiro);
        refreshOk(segundo);
    }

    // ---- armazenamento --------------------------------------------------------------------------------------

    @Test
    void bancoGuardaSomenteOHashEPorTrintaDias() throws Exception {
        String refresh = login("aplicador1@example.com").get("refreshToken").asText();

        assertThat(linhasComHash(sha256(refresh))).isEqualTo(1);
        assertThat(linhasComHash(refresh)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where token_hash like ?", Integer.class,
                "%" + refresh + "%")).isZero();
        OffsetDateTime expiraEm = jdbc.queryForObject("select expira_em from refresh_token where token_hash = ?", OffsetDateTime.class,
                sha256(refresh));
        assertThat(Duration.between(Instant.now(), expiraEm.toInstant()))
                .isBetween(Duration.ofDays(30).minusMinutes(2), Duration.ofDays(30).plusMinutes(1));
    }

    // ---- refresh ---------------------------------------------------------------------------------------------

    @Test
    void refreshDevolveNovoParEInvalidaOAnteriorPorRotacao() throws Exception {
        String antigo = login("aplicador1@example.com").get("refreshToken").asText();

        JsonNode par = refreshOk(antigo);

        String novo = par.get("refreshToken").asText();
        assertThat(novo).isNotEqualTo(antigo).hasSize(43);
        assertThat(par.get("accessToken").asText()).contains(".");
        assertThat(par.size()).isEqualTo(2);
        assertThat(linhasComHash(sha256(antigo))).isZero();
        assertThat(linhasComHash(sha256(novo))).isEqualTo(1);
        refreshInvalido(refresh(antigo));
        refreshOk(novo);
    }

    @Test
    void comAccessTokenExpiradoORefreshDevolveUmAccessQueFunciona() throws Exception {
        String refresh = login("aplicador1@example.com").get("refreshToken").asText();
        String expirado = tokens.token(TokensDeTeste.ID_APLICADOR, "gaspar-sc", "APLICADOR",
                Instant.now().minus(Duration.ofHours(2)), Duration.ofMinutes(15));
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expirado))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));

        String novoAccess = refreshOk(refresh).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + novoAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TokensDeTeste.ID_APLICADOR))
                .andExpect(jsonPath("$.perfil").value("APLICADOR"));
        Jwt jwt = decoder.decode(novoAccess);
        assertThat(jwt.getSubject()).isEqualTo(TokensDeTeste.ID_APLICADOR);
        assertThat(jwt.getClaimAsString("tenantId")).isEqualTo("gaspar-sc");
        assertThat(jwt.getClaimAsString("perfil")).isEqualTo("APLICADOR");
        assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(900);
    }

    @Test
    void refreshDesconhecidoOuLixoRetornaRefreshInvalido() throws Exception {
        refreshInvalido(refresh("valor-que-nunca-foi-emitido"));
        refreshInvalido(refresh("a".repeat(43)));
        refreshInvalido(refresh("um.jwt.qualquer"));
    }

    @Test
    void refreshExpiradoEhRecusadoERemovidoDoBanco() throws Exception {
        String refresh = login("aplicador1@example.com").get("refreshToken").asText();
        entityManager.flush();
        jdbc.update("update refresh_token set expira_em = ? where token_hash = ?",
                OffsetDateTime.now().minusMinutes(1), sha256(refresh));
        entityManager.clear();

        refreshInvalido(refresh(refresh));

        assertThat(linhasComHash(sha256(refresh))).isZero();
    }

    @Test
    void refreshRemovidoManualmenteDoBancoRetornaRefreshInvalido() throws Exception {
        String refresh = login("aplicador1@example.com").get("refreshToken").asText();
        entityManager.flush();
        jdbc.update("delete from refresh_token where token_hash = ?", sha256(refresh));
        entityManager.clear();

        refreshInvalido(refresh(refresh));
    }

    @Test
    void usuarioDesativadoDepoisDoLoginNaoRenova() throws Exception {
        String refresh = login("aplicador1@example.com").get("refreshToken").asText();
        entityManager.flush();
        jdbc.update("update usuario set status = 'DESATIVADO' where id = ?",
                java.util.UUID.fromString(TokensDeTeste.ID_APLICADOR));
        entityManager.clear();

        refreshInvalido(refresh(refresh));
    }

    @Test
    void refreshForjadoParaAdministradorNaoRenova() throws Exception {
        jdbc.update("""
                insert into refresh_token (id, tenant_id, usuario_id, token_hash, expira_em)
                values (random_uuid(), 'gaspar-sc', ?, ?, ?)
                """, java.util.UUID.fromString(TokensDeTeste.ID_ADMINISTRADOR), sha256("valor-forjado"),
                OffsetDateTime.now().plusDays(1));

        refreshInvalido(refresh("valor-forjado"));
    }

    @Test
    void campoAusenteOuEmBrancoRetornaValidacaoInvalida() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"))
                .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));
        refresh("   ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDACAO_INVALIDA"));
        refresh("x".repeat(201)).andExpect(status().isBadRequest());
    }

    @Test
    void refreshEPublicoNaoExigeAuthorization() throws Exception {
        String refresh = login("aplicador1@example.com").get("refreshToken").asText();

        // refreshOk não envia o header Authorization
        assertThat(refreshOk(refresh).has("accessToken")).isTrue();
    }

    // ---- sigilo -----------------------------------------------------------------------------------------------

    @Test
    void valorDoRefreshNuncaApareceNosLogs(CapturedOutput saida) throws Exception {
        String refresh = login("aplicador1@example.com").get("refreshToken").asText();
        String novo = refreshOk(refresh).get("refreshToken").asText();
        refreshInvalido(refresh(refresh));
        refreshInvalido(refresh("valor-invalido-para-log"));
        entityManager.flush();

        assertThat(saida.getAll()).doesNotContain(refresh).doesNotContain(novo)
                .doesNotContain("valor-invalido-para-log");
    }
}
