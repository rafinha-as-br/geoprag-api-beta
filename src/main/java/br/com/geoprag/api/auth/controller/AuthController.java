package br.com.geoprag.api.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.geoprag.api.auth.dto.LoginRequest;
import br.com.geoprag.api.auth.dto.LoginResponse;
import br.com.geoprag.api.auth.dto.RefreshRequest;
import br.com.geoprag.api.auth.dto.RefreshResponse;
import br.com.geoprag.api.auth.dto.UsuarioResponse;
import br.com.geoprag.api.auth.service.AuthService;
import br.com.geoprag.api.auth.service.RefreshTokenService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;
    private final RefreshTokenService refreshTokens;

    public AuthController(AuthService service, RefreshTokenService refreshTokens) {
        this.service = service;
        this.refreshTokens = refreshTokens;
    }

    @Operation(summary = "Autentica por CPF ou e-mail e emite o access token (público)")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest pedido) {
        return service.login(pedido);
    }

    @Operation(summary = "Troca um refresh token válido por um novo par de tokens (público; só o aplicador tem refresh)")
    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest pedido) {
        return refreshTokens.renovar(pedido.refreshToken());
    }

    @Operation(summary = "Usuário autenticado pelo token (qualquer perfil)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt token) {
        return service.usuarioAutenticado(token.getSubject(), token.getClaimAsString("tenantId"));
    }
}
