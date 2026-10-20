package br.com.geoprag.api.ponto.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.geoprag.api.auth.security.DadosDoToken;
import br.com.geoprag.api.ponto.dto.BairroResponse;
import br.com.geoprag.api.ponto.service.ReferenciasService;

/** Bairros do tenant (extensão registrada do contrato v0); só administrador e sub-administrador. */
@RestController
@RequestMapping("/api/v1/bairros")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','SUB_ADMINISTRADOR')")
@SecurityRequirement(name = "bearerAuth")
public class BairroController {

    private final ReferenciasService service;

    public BairroController(ReferenciasService service) {
        this.service = service;
    }

    @Operation(summary = "Lista os bairros do tenant, em ordem alfabética")
    @GetMapping
    public List<BairroResponse> listar(@AuthenticationPrincipal Jwt token) {
        return service.bairros(DadosDoToken.tenantId(token));
    }
}
