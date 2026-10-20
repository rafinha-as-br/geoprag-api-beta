package br.com.geoprag.api.ponto.dto;

import br.com.geoprag.api.ponto.domain.Bairro;

/** Modelo {@code Bairro} (extensão registrada do contrato v0). */
public record BairroResponse(String id, String nome) {

    public static BairroResponse de(Bairro bairro) {
        return new BairroResponse(bairro.getId().toString(), bairro.getNome());
    }
}
