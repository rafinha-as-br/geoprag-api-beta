package br.com.geoprag.api.auth.service;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.auth.domain.Cpf;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.dto.LoginRequest;
import br.com.geoprag.api.auth.dto.LoginResponse;
import br.com.geoprag.api.auth.dto.UsuarioResponse;
import br.com.geoprag.api.auth.repository.UsuarioRepository;
import br.com.geoprag.api.common.error.ApiException;
import br.com.geoprag.api.common.error.ErrorCode;

/**
 * Login por CPF ou e-mail e consulta do usuário autenticado. Nunca registra o identificador (pode ser CPF)
 * nem a senha, e responde igual a usuário inexistente e a senha errada.
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    /** Conferido quando o usuário não existe, para o tempo de resposta não revelar se o identificador é válido. */
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest pedido) {
        Optional<Usuario> encontrado = buscarPorIdentificador(pedido.identificador());
        boolean senhaConfere = passwordEncoder.matches(pedido.senha(),
                encontrado.map(Usuario::getSenhaHash).orElse(hashFicticio));
        if (encontrado.isEmpty() || !senhaConfere) {
            throw new ApiException(ErrorCode.CREDENCIAIS_INVALIDAS, HttpStatus.UNAUTHORIZED);
        }
        Usuario usuario = encontrado.get();
        if (!usuario.estaAtivo()) {
            throw new ApiException(ErrorCode.USUARIO_DESATIVADO, HttpStatus.FORBIDDEN);
        }
        TokenService.TokenEmitido token = tokens.emitir(usuario);
        return new LoginResponse(token.valor(), token.expiresInSeconds(), UsuarioResponse.de(usuario));
    }

    /**
     * Usuário dono do token. O tenant vem do token: id de outro tenant não é encontrado, e usuário
     * desativado depois da emissão do token deixa de ter acesso.
     */
    @Transactional(readOnly = true)
    public UsuarioResponse usuarioAutenticado(String usuarioId, String tenantId) {
        Usuario usuario = idValido(usuarioId)
                .flatMap(id -> usuarios.findByIdAndTenantId(id, tenantId))
                .orElseThrow(() -> new ApiException(ErrorCode.NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED));
        if (!usuario.estaAtivo()) {
            throw new ApiException(ErrorCode.USUARIO_DESATIVADO, HttpStatus.FORBIDDEN);
        }
        return UsuarioResponse.de(usuario);
    }

    /** E-mail se tiver {@code @}; caso contrário, CPF (com ou sem máscara). Formato irreconhecível não encontra ninguém. */
    private Optional<Usuario> buscarPorIdentificador(String identificador) {
        String valor = identificador.trim();
        if (valor.contains("@")) {
            return usuarios.findByEmail(valor.toLowerCase(Locale.ROOT));
        }
        try {
            return usuarios.findByCpf(Cpf.normalizar(valor));
        } catch (IllegalArgumentException cpfMalFormado) {
            return Optional.empty();
        }
    }

    private static Optional<UUID> idValido(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException | NullPointerException idInvalido) {
            return Optional.empty();
        }
    }
}
