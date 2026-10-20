package br.com.geoprag.api.sync.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resultado de um item (contrato v0). Propositalmente <b>sem</b> campo de conflito: o conflito é aceito e marcado
 * no servidor, visível ao gestor e nunca ao aplicador.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultadoDoItem(String id, StatusItem status, CodigoRejeicao codigo, String motivo) {

    public static ResultadoDoItem criado(String id) {
        return new ResultadoDoItem(id, StatusItem.CRIADO, null, null);
    }

    public static ResultadoDoItem duplicado(String id) {
        return new ResultadoDoItem(id, StatusItem.DUPLICADO, null, null);
    }

    public static ResultadoDoItem rejeitado(String id, CodigoRejeicao codigo, String motivo) {
        return new ResultadoDoItem(id, StatusItem.REJEITADO, codigo, motivo);
    }
}
