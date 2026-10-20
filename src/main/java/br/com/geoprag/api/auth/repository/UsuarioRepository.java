package br.com.geoprag.api.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    /** O e-mail é guardado em minúsculas: quem chama deve normalizar a busca. */
    Optional<Usuario> findByEmail(String email);

    /** Somente dígitos (ver {@code Cpf.normalizar}). */
    Optional<Usuario> findByCpf(String cpf);

    /** Busca restrita ao tenant (o do token): usuário de outro tenant nunca é encontrado. */
    Optional<Usuario> findByIdAndTenantId(UUID id, String tenantId);

    List<Usuario> findByTenantIdAndPerfil(String tenantId, Perfil perfil);
}
