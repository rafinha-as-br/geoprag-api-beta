package br.com.geoprag.api.common.cors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

/** Testes sem contexto Spring: cobrem o padrão da aplicação (CORS_ALLOWED_ORIGINS vazio). */
class CorsConfigTests {

    @Test
    void semOrigensConfiguradasNenhumMapeamentoCorsEhRegistrado() {
        assertThat(mapeamentosPara(new CorsProperties(List.of()))).isEmpty();
        assertThat(mapeamentosPara(new CorsProperties(null))).isEmpty();
    }

    @Test
    void comOrigensConfiguradasRegistraMapeamentoParaTodasAsRotas() {
        Map<String, CorsConfiguration> mapeamentos =
                mapeamentosPara(new CorsProperties(List.of("http://localhost:3000", "https://portal.exemplo.com")));

        assertThat(mapeamentos).containsOnlyKeys("/**");
        CorsConfiguration config = mapeamentos.get("/**");
        assertThat(config.getAllowedOrigins())
                .containsExactly("http://localhost:3000", "https://portal.exemplo.com");
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "OPTIONS");
        assertThat(config.getAllowCredentials()).isFalse();
    }

    @Test
    void propriedadesIgnoramEspacosEEntradasEmBranco() {
        // CORS_ALLOWED_ORIGINS="a, b,,  " chega ao binding como lista com espaços e itens vazios
        CorsProperties props = new CorsProperties(Arrays.asList(" http://a.example ", "", "  "));

        assertThat(props.allowedOrigins()).containsExactly("http://a.example");
    }

    private static Map<String, CorsConfiguration> mapeamentosPara(CorsProperties properties) {
        RegistryInspecionavel registry = new RegistryInspecionavel();
        new CorsConfig(properties).addCorsMappings(registry);
        return registry.mapeamentos();
    }

    /** {@code CorsRegistry#getCorsConfigurations} é protegido; esta subclasse o expõe ao teste. */
    private static class RegistryInspecionavel extends CorsRegistry {
        Map<String, CorsConfiguration> mapeamentos() {
            return getCorsConfigurations();
        }
    }
}
