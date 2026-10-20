package br.com.geoprag.api.ponto.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.geoprag.api.auth.security.DadosDoToken;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.dto.AtivarPontoRequest;
import br.com.geoprag.api.ponto.dto.AtribuirAplicadorRequest;
import br.com.geoprag.api.ponto.dto.PontoResponse;
import br.com.geoprag.api.ponto.service.PontoService;
import br.com.geoprag.api.ponto.service.TransicaoDePontoService;

/** Pontos para o portal (leitura e transições de estado); só administrador e sub-administrador. Tenant do token. */
@RestController
@RequestMapping("/api/v1/pontos")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','SUB_ADMINISTRADOR')")
@SecurityRequirement(name = "bearerAuth")
public class PontoController {

    private final PontoService service;
    private final TransicaoDePontoService transicoes;

    public PontoController(PontoService service, TransicaoDePontoService transicoes) {
        this.service = service;
        this.transicoes = transicoes;
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

    @Operation(summary = "Atribui ou troca o aplicador do ponto (409 se o estado não permitir)")
    @PostMapping("/{id}/aplicador")
    public PontoResponse atribuirAplicador(@AuthenticationPrincipal Jwt token, @PathVariable String id,
            @Valid @RequestBody AtribuirAplicadorRequest corpo) {
        return transicoes.atribuirAplicador(DadosDoToken.tenantId(token), id, corpo.aplicadorId());
    }

    @Operation(summary = "Remove o aplicador do ponto (só em ponto direcionado; 409 nos demais estados)")
    @DeleteMapping("/{id}/aplicador")
    public PontoResponse removerAplicador(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        return transicoes.removerAplicador(DadosDoToken.tenantId(token), id);
    }

    @Operation(summary = "Ativa o ponto e gera as datas do agendamento (409 se o estado não permitir)")
    @PostMapping("/{id}/ativacao")
    public PontoResponse ativar(@AuthenticationPrincipal Jwt token, @PathVariable String id,
            @Valid @RequestBody AtivarPontoRequest corpo) {
        return transicoes.ativar(DadosDoToken.tenantId(token), id, corpo.dataInicio(), corpo.intervaloDias(),
                corpo.quantidadeRecorrencias());
    }
}
