package br.com.geoprag.api.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.RefreshToken;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.dto.RefreshResponse;
import br.com.geoprag.api.auth.repository.RefreshTokenRepository;
import br.com.geoprag.api.common.error.ApiException;
import br.com.geoprag.api.common.error.ErrorCode;

/**
 * Refresh token opaco do aplicador: 30 dias, sem revogação, rotação a cada uso. O valor em texto puro só existe
 * na resposta ao app; no banco fica o hash, e nada disso é registrado em log.
 */
@Service
public class RefreshTokenService {

    static final Duration VALIDADE = Duration.ofDays(30);
    private static final int TAMANHO_EM_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final TokenService accessTokens;
    private final SecureRandom aleatorio = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository tokens, TokenService accessTokens) {
        this.tokens = tokens;
        this.accessTokens = accessTokens;
    }

    /** Emite um refresh token novo para o usuário e devolve o valor em texto puro (única vez em que existe). */
    @Transactional
    public String emitir(Usuario usuario) {
        byte[] bytes = new byte[TAMANHO_EM_BYTES];
        aleatorio.nextBytes(bytes);
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(usuario, hash(valor), Instant.now().plus(VALIDADE)));
        return valor;
    }

    /**
     * Troca um refresh token válido por um novo par de tokens e invalida o anterior (rotação). Inválido, expirado,
     * removido, de usuário desativado ou fora do perfil {@code APLICADOR} responde {@code 401 REFRESH_INVALIDO}.
     * A remoção de um token expirado persiste mesmo com a exceção ({@code noRollbackFor}).
     */
    @Transactional(noRollbackFor = ApiException.class)
    public RefreshResponse renovar(String valor) {
        RefreshToken atual = tokens.findByTokenHash(hash(valor)).orElseThrow(RefreshTokenService::invalido);
        Usuario usuario = atual.getUsuario();
        if (atual.expirou(Instant.now())) {
            tokens.delete(atual);
            throw invalido();
        }
        if (!usuario.estaAtivo() || usuario.getPerfil() != Perfil.APLICADOR) {
            throw invalido();
        }
        tokens.delete(atual);
        String novoRefresh = emitir(usuario);
        return new RefreshResponse(accessTokens.emitir(usuario).valor(), novoRefresh);
    }

    /** SHA-256 em hexadecimal (64 caracteres), o que fica no banco. */
    static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static ApiException invalido() {
        return new ApiException(ErrorCode.REFRESH_INVALIDO, HttpStatus.UNAUTHORIZED);
    }
}
