package br.com.geoprag.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import br.com.geoprag.api.auth.config.JwtProperties;

/** Fail-fast para segredo ausente ou fraco (sem contexto completo). */
class JwtPropertiesTests {

    private static final String SEGREDO_FORTE = "0123456789abcdef0123456789abcdef";

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner contexto = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void segredoAusenteOuEmBrancoImpedeAInicializacao() {
        assertThatThrownBy(() -> new JwtProperties(null, 15)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("", 15)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProperties("   ", 15)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void segredoComMenosDe32BytesImpedeAInicializacao() {
        assertThatThrownBy(() -> new JwtProperties("curto", 15)).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new JwtProperties(SEGREDO_FORTE, 15)).doesNotThrowAnyException();
    }

    @Test
    void mensagemDeErroNaoVazaOSegredo() {
        assertThatThrownBy(() -> new JwtProperties("segredo-curto-demais", 15))
                .hasMessageContaining("JWT_SECRET")
                .hasMessageNotContaining("segredo-curto-demais");
    }

    @Test
    void validadeDoTokenDeveSerPositiva() {
        assertThatThrownBy(() -> new JwtProperties(SEGREDO_FORTE, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void contextoNaoSobeSemSegredo() {
        contexto.withPropertyValues("app.jwt.access-token-minutes=15")
                .run(ctx -> assertThat(ctx).hasFailed());
        contexto.withPropertyValues("app.jwt.secret=", "app.jwt.access-token-minutes=15")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void contextoSobeComSegredoForte() {
        contexto.withPropertyValues("app.jwt.secret=" + SEGREDO_FORTE, "app.jwt.access-token-minutes=15")
                .run(ctx -> assertThat(ctx.getBean(JwtProperties.class).accessTokenMinutes()).isEqualTo(15));
    }
}
