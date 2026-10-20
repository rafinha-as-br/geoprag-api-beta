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
import br.com.geoprag.api.auth.dto.UsuarioResponse;
import br.com.geoprag.api.auth.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @Operation(summary = "Autentica por CPF ou e-mail e emite o access token (público)")
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest pedido) {
        return service.login(pedido);
    }

    @Operation(summary = "Usuário autenticado pelo token (qualquer perfil)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal Jwt token) {
        return service.usuarioAutenticado(token.getSubject(), token.getClaimAsString("tenantId"));
    }
}
