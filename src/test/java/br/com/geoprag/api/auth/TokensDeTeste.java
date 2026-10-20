package br.com.geoprag.api.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Gera tokens assinados com o segredo de teste, para exercitar autorização sem passar pelo login. */
@Component
public class TokensDeTeste {

    /** Ids fixos do seed (ver {@code db/seed}). */
    public static final String ID_ADMINISTRADOR = "00000000-0000-4000-8000-000000000001";
    public static final String ID_SUB_ADMINISTRADOR = "00000000-0000-4000-8000-000000000002";
    public static final String ID_APLICADOR = "00000000-0000-4000-8000-000000000003";
    public static final String ID_APLICADOR_DESATIVADO = "00000000-0000-4000-8000-000000000005";

    private final JwtEncoder encoder;

    TokensDeTeste(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    /** Valor do header {@code Authorization} para um token válido de 15 minutos. */
    public String bearer(String usuarioId, String tenantId, String perfil) {
        return "Bearer " + token(usuarioId, tenantId, perfil, Instant.now(), Duration.ofMinutes(15));
    }

    public String bearerAdministrador() {
        return bearer(ID_ADMINISTRADOR, "gaspar-sc", "ADMINISTRADOR");
    }

    public String bearerAplicador() {
        return bearer(ID_APLICADOR, "gaspar-sc", "APLICADOR");
    }

    public String token(String usuarioId, String tenantId, String perfil, Instant emitidoEm, Duration validade) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(usuarioId)
                .issuedAt(emitidoEm)
                .expiresAt(emitidoEm.plus(validade));
        if (tenantId != null) {
            claims.claim("tenantId", tenantId);
        }
        if (perfil != null) {
            claims.claim("perfil", perfil);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
