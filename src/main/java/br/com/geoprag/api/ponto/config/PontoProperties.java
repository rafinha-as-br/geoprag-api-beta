package br.com.geoprag.api.ponto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração dos pontos. {@code maxRecorrencias} é o teto de datas geradas em {@code POST /pontos/{id}/ativacao}
 * (variável {@code PONTO_MAX_RECORRENCIAS}, padrão 365); acima dele o pedido recebe {@code 400 VALIDACAO_INVALIDA}.
 */
@ConfigurationProperties(prefix = "app.ponto")
public record PontoProperties(@DefaultValue("365") int maxRecorrencias) {

    public PontoProperties {
        if (maxRecorrencias <= 0) {
            throw new IllegalStateException("PONTO_MAX_RECORRENCIAS deve ser maior que zero.");
        }
    }

    @Configuration
    @EnableConfigurationProperties(PontoProperties.class)
    static class Ativacao {
    }
}
