package br.com.geoprag.api.ponto.domain;

/** Ação do administrador sobre um ponto que gera evento na trilha de auditoria. */
public enum AcaoDePonto {
    CRIACAO,
    EDICAO,
    ATRIBUICAO_APLICADOR,
    REMOCAO_APLICADOR,
    ATIVACAO,
    DESATIVACAO,
    REATIVACAO,
    CANCELAMENTO,
    REMOCAO_ALERTA_ATRASO
}
