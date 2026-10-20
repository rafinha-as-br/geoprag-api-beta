package br.com.geoprag.api.sync.domain;

/**
 * Motivo pelo qual o servidor aceitou um registro e o marcou como conflito. A ordem é a prioridade quando mais de
 * um motivo se aplica: vale o primeiro. O conflito é visível ao gestor, nunca ao aplicador.
 */
public enum MotivoConflito {
    /** O ponto foi atribuído a outro aplicador (ou ficou sem aplicador) enquanto este estava offline. */
    PONTO_REATRIBUIDO,
    PONTO_DESATIVADO,
    /** A aplicação do ponto foi cancelada. */
    PONTO_INATIVO,
    /** Já existe outra aplicação para a mesma data agendada. */
    DATA_JA_ATENDIDA,
    DATA_CANCELADA
}
