package br.com.geoprag.api.ponto.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.geoprag.api.auth.domain.StatusUsuario;
import br.com.geoprag.api.auth.security.DadosDoToken;
import br.com.geoprag.api.ponto.dto.AplicadorResumoResponse;
import br.com.geoprag.api.ponto.service.ReferenciasService;

/** Aplicadores do tenant, para a atribuição a um ponto; só administrador e sub-administrador. */
@RestController
@RequestMapping("/api/v1/aplicadores")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','SUB_ADMINISTRADOR')")
@SecurityRequirement(name = "bearerAuth")
public class AplicadorController {

    private final ReferenciasService service;

    public AplicadorController(ReferenciasService service) {
        this.service = service;
    }

    @Operation(summary = "Lista os aplicadores do tenant, com filtro opcional por status")
    @GetMapping
    public List<AplicadorResumoResponse> listar(@AuthenticationPrincipal Jwt token,
            @RequestParam(required = false) StatusUsuario status) {
        return service.aplicadores(DadosDoToken.tenantId(token), status);
    }
}
