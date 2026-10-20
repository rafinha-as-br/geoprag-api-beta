package br.com.geoprag.api.sync.dto;

/** Resultado do processamento de um item (contrato v0). {@code DUPLICADO} é sucesso para o app. */
public enum StatusItem {
    CRIADO,
    DUPLICADO,
    REJEITADO
}
