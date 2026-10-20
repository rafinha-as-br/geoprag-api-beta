package br.com.geoprag.api.sync.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.geoprag.api.auth.security.DadosDoToken;
import br.com.geoprag.api.sync.dto.LoteDeRegistro;
import br.com.geoprag.api.sync.dto.RespostaDoLote;
import br.com.geoprag.api.sync.service.SincronizacaoService;

/** Sincronização do App Aplicador; só o perfil {@code APLICADOR}. Tenant e aplicador vêm do token. */
@RestController
@RequestMapping("/api/v1/sync")
@PreAuthorize("hasRole('APLICADOR')")
@SecurityRequirement(name = "bearerAuth")
public class SyncController {

    private final SincronizacaoService service;

    public SyncController(SincronizacaoService service) {
        this.service = service;
    }

    @Operation(summary = "Envia em lote os registros de aplicação, com um resultado por item "
            + "(CRIADO, DUPLICADO ou REJEITADO); o registro online usa o mesmo envio, com um item")
    @PostMapping("/aplicacoes")
    public RespostaDoLote enviar(@AuthenticationPrincipal Jwt token, @Valid @RequestBody LoteDeRegistro lote) {
        return service.registrarLote(DadosDoToken.tenantId(token), DadosDoToken.usuarioId(token), lote);
    }
}
