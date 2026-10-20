package br.com.geoprag.api.auth.dto;

import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.StatusUsuario;
import br.com.geoprag.api.auth.domain.Usuario;

/** Modelo {@code Usuario} do contrato v0. Não expõe CPF nem hash de senha. */
public record UsuarioResponse(String id, String nome, Perfil perfil, String tenantId, String email,
        StatusUsuario status) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId().toString(), usuario.getNome(), usuario.getPerfil(),
                usuario.getTenant().getId(), usuario.getEmail(), usuario.getStatus());
    }
}
