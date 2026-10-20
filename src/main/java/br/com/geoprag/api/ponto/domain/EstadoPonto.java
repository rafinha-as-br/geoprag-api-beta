package br.com.geoprag.api.ponto.domain;

/** Estado do ponto de aplicação (valores do contrato v0 e das RNs). O servidor é a autoridade sobre o estado. */
public enum EstadoPonto {
    ENDERECADA,
    DIRECIONADA,
    ATIVA,
    INATIVA,
    DESATIVADO
}
