package br.com.geoprag.api.ponto.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.geoprag.api.auth.security.DadosDoToken;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.dto.PontoResponse;
import br.com.geoprag.api.ponto.service.PontoService;

/** Leitura de pontos para o portal; só administrador e sub-administrador. O tenant vem do token. */
@RestController
@RequestMapping("/api/v1/pontos")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','SUB_ADMINISTRADOR')")
@SecurityRequirement(name = "bearerAuth")
public class PontoController {

    private final PontoService service;

    public PontoController(PontoService service) {
        this.service = service;
    }

    @Operation(summary = "Lista os pontos do tenant, com filtros opcionais por bairro (nome) e estado")
    @GetMapping
    public List<PontoResponse> listar(@AuthenticationPrincipal Jwt token,
            @RequestParam(required = false) String bairro, @RequestParam(required = false) EstadoPonto estado) {
        return service.listar(DadosDoToken.tenantId(token), bairro, estado);
    }

    @Operation(summary = "Detalha um ponto (404 se não existir ou for de outro tenant)")
    @GetMapping("/{id}")
    public PontoResponse obter(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        return service.obter(DadosDoToken.tenantId(token), id);
    }
}
