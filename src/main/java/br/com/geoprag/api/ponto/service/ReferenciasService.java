package br.com.geoprag.api.ponto.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.StatusUsuario;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.repository.UsuarioRepository;
import br.com.geoprag.api.ponto.dto.AplicadorResumoResponse;
import br.com.geoprag.api.ponto.dto.BairroResponse;
import br.com.geoprag.api.ponto.repository.BairroRepository;

/** Listas de apoio do portal (bairros e aplicadores), sempre do tenant do token. */
@Service
@Transactional(readOnly = true)
public class ReferenciasService {

    private final BairroRepository bairros;
    private final UsuarioRepository usuarios;

    public ReferenciasService(BairroRepository bairros, UsuarioRepository usuarios) {
        this.bairros = bairros;
        this.usuarios = usuarios;
    }

    public List<BairroResponse> bairros(String tenantId) {
        return bairros.findByTenantIdOrderByNome(tenantId).stream().map(BairroResponse::de).toList();
    }

    /** Aplicadores do tenant (nunca administradores), ordenados pelo nome, com filtro opcional por status. */
    public List<AplicadorResumoResponse> aplicadores(String tenantId, StatusUsuario status) {
        return usuarios.findByTenantIdAndPerfil(tenantId, Perfil.APLICADOR).stream()
                .filter(u -> status == null || u.getStatus() == status)
                .sorted(Comparator.comparing(Usuario::getNome))
                .map(AplicadorResumoResponse::de)
                .toList();
    }
}
