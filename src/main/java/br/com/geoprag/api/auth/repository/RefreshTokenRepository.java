package br.com.geoprag.api.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

import br.com.geoprag.api.auth.domain.RefreshToken;

/**
 * A busca é pelo hash do token (único no banco): o endpoint de refresh é público e o tenant sai do próprio
 * registro. Estende {@link Repository}, e não {@code JpaRepository}, para expor só o necessário.
 */
public interface RefreshTokenRepository extends Repository<RefreshToken, UUID> {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void delete(RefreshToken refreshToken);
}
