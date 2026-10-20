package br.com.geoprag.api.ponto.service;

import org.springframework.stereotype.Component;

import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.StatusUsuario;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.repository.UsuarioRepository;
import br.com.geoprag.api.common.error.ApiException;

/**
 * Resolve o aplicador informado pelo administrador. Inexistente, de outro tenant ou que não tem o perfil
 * {@code APLICADOR}: {@code 400 VALIDACAO_INVALIDA} no campo {@code aplicadorId} (sem revelar outro tenant);
 * aplicador {@code DESATIVADO} do próprio tenant: {@code 409 OPERACAO_NAO_PERMITIDA}.
 */
@Component
class AplicadorDoTenant {

    private final UsuarioRepository usuarios;

    AplicadorDoTenant(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    Usuario resolver(String tenantId, String aplicadorId) {
        Usuario aplicador = PontoService.uuid(aplicadorId)
                .flatMap(id -> usuarios.findByIdAndTenantId(id, tenantId))
                .filter(u -> u.getPerfil() == Perfil.APLICADOR)
                .orElseThrow(() -> ApiException.validacao("aplicadorId", "Informe um aplicador válido."));
        if (aplicador.getStatus() == StatusUsuario.DESATIVADO) {
            throw ApiException.operacaoNaoPermitida("O aplicador está desativado e não pode ser atribuído a um ponto.");
        }
        return aplicador;
    }
}
