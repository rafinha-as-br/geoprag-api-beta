package br.com.geoprag.api.sync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração da sincronização.
 * <ul>
 * <li>{@code maxItensPorLote}: limite de itens em {@code POST /sync/aplicacoes} (variável
 * {@code SYNC_MAX_ITENS_POR_LOTE}, padrão 500); acima dele o pedido recebe {@code 413 LOTE_MUITO_GRANDE}.</li>
 * <li>{@code margemCursorSegundos}: quanto o {@code cursor} do pull recua em relação ao instante do servidor
 * ({@code SYNC_MARGEM_CURSOR_SEGUNDOS}, padrão 5). Cobre uma transação que gravou a mudança mas ainda não tinha
 * confirmado quando o pull leu; o app pode receber de novo um item já visto (o upsert é idempotente).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(@DefaultValue("500") int maxItensPorLote, @DefaultValue("5") int margemCursorSegundos) {

    public SyncProperties {
        if (maxItensPorLote <= 0) {
            throw new IllegalStateException("SYNC_MAX_ITENS_POR_LOTE deve ser maior que zero.");
        }
        if (margemCursorSegundos < 0) {
            throw new IllegalStateException("SYNC_MARGEM_CURSOR_SEGUNDOS não pode ser negativo.");
        }
    }

    @Configuration
    @EnableConfigurationProperties(SyncProperties.class)
    static class Ativacao {
    }
}
