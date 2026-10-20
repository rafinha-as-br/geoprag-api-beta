package br.com.geoprag.api.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração da sincronização. {@code maxItensPorLote} é o limite de itens em {@code POST /sync/aplicacoes}
 * (variável {@code SYNC_MAX_ITENS_POR_LOTE}, padrão 500); acima dele o pedido recebe {@code 413 LOTE_MUITO_GRANDE}.
 */
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(@DefaultValue("500") int maxItensPorLote) {

    public SyncProperties {
        if (maxItensPorLote <= 0) {
            throw new IllegalStateException("SYNC_MAX_ITENS_POR_LOTE deve ser maior que zero.");
        }
    }

    @Configuration
    @EnableConfigurationProperties(SyncProperties.class)
    static class Ativacao {
    }
}
