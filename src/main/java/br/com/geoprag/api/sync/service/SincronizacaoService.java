package br.com.geoprag.api.sync.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.geoprag.api.common.error.ApiException;
import br.com.geoprag.api.common.error.ErrorCode;
import br.com.geoprag.api.sync.config.SyncProperties;
import br.com.geoprag.api.sync.dto.ItemDeRegistro;
import br.com.geoprag.api.sync.dto.LoteDeRegistro;
import br.com.geoprag.api.sync.dto.RespostaDoLote;
import br.com.geoprag.api.sync.dto.ResultadoDoItem;

/**
 * Envio em lote dos registros feitos em campo. <b>Este método não é transacional de propósito</b>: cada item é
 * processado por {@link RegistroAplicacaoService#registrar}, que abre e fecha a própria transação. Assim um item
 * rejeitado não derruba os demais e o que já foi gravado antes de uma falha inesperada permanece (o app reenvia o
 * lote e os itens gravados voltam como {@code DUPLICADO}). Uma falha inesperada de infraestrutura não é mascarada
 * como resultado de item: propaga como erro do servidor.
 */
@Service
public class SincronizacaoService {

    private final RegistroAplicacaoService registro;
    private final SyncProperties propriedades;

    public SincronizacaoService(RegistroAplicacaoService registro, SyncProperties propriedades) {
        this.registro = registro;
        this.propriedades = propriedades;
    }

    /**
     * @param tenantId    tenant do token
     * @param aplicadorId usuário do token: o aplicador de todos os itens (o corpo nunca o informa)
     */
    public RespostaDoLote registrarLote(String tenantId, UUID aplicadorId, LoteDeRegistro lote) {
        if (lote.itens().size() > propriedades.maxItensPorLote()) {
            throw new ApiException(ErrorCode.LOTE_MUITO_GRANDE, HttpStatus.PAYLOAD_TOO_LARGE);
        }
        OffsetDateTime recebidoEm = OffsetDateTime.now(ZoneOffset.UTC);
        List<ResultadoDoItem> resultados = new ArrayList<>(lote.itens().size());
        for (ItemDeRegistro item : lote.itens()) {
            resultados.add(registro.registrar(tenantId, aplicadorId, item));
        }
        return new RespostaDoLote(recebidoEm, List.copyOf(resultados));
    }
}
