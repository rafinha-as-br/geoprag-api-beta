package br.com.geoprag.api.ponto.dto;

import br.com.geoprag.api.auth.domain.StatusUsuario;
import br.com.geoprag.api.auth.domain.Usuario;

/** Modelo {@code AplicadorResumo} do contrato v0. Nunca expõe CPF, e-mail nem hash de senha. */
public record AplicadorResumoResponse(String id, String nome, StatusUsuario status) {

    public static AplicadorResumoResponse de(Usuario aplicador) {
        return new AplicadorResumoResponse(aplicador.getId().toString(), aplicador.getNome(), aplicador.getStatus());
    }
}
