package br.com.geoprag.api.auth.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.geoprag.api.auth.config.JwtProperties;
import br.com.geoprag.api.auth.domain.Usuario;

/** Emite o access token (JWT HS256) com {@code sub} (id do usuário), {@code tenantId} e {@code perfil}. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Duration validade;

    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.validade = Duration.ofMinutes(properties.accessTokenMinutes());
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant agora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(usuario.getId().toString())
                .claim("tenantId", usuario.getTenant().getId())
                .claim("perfil", usuario.getPerfil().name())
                .issuedAt(agora)
                .expiresAt(agora.plus(validade))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenEmitido(token, validade.toSeconds());
    }

    public record TokenEmitido(String valor, long expiresInSeconds) {
    }
}
