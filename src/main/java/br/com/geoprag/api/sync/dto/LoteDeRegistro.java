package br.com.geoprag.api.sync.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code POST /api/v1/sync/aplicacoes} (contrato v0). Só o envelope é validado aqui; cada item é
 * validado pelo serviço de registro, que rejeita apenas o item inválido sem derrubar o lote.
 */
public record LoteDeRegistro(
        @NotBlank @Size(max = 100) String dispositivoId,
        @NotEmpty List<ItemDeRegistro> itens) {
}
