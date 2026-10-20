package br.com.geoprag.api.sync.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import br.com.geoprag.api.auth.domain.Tenant;

/**
 * Cada item de registro enviado pelo app. <b>Append-only</b>: depois de gravado nunca é editado nem apagado (todas
 * as colunas são {@code updatable = false} e a entidade não tem setters). O {@code id} é o UUID gerado no app.
 * O {@code recebidoEm} é sempre o relógio do servidor, definido na criação.
 */
@Entity
@Table(name = "subponto")
public class Subponto implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aplicacao_id", nullable = false, updatable = false)
    private Aplicacao aplicacao;

    @Column(name = "latitude", precision = 9, scale = 6, nullable = false, updatable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6, nullable = false, updatable = false)
    private BigDecimal longitude;

    @Column(name = "realizado_em", nullable = false, updatable = false)
    private Instant realizadoEm;

    @Column(name = "recebido_em", nullable = false, updatable = false)
    private Instant recebidoEm;

    @Column(name = "conflito", nullable = false, updatable = false)
    private boolean conflito;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_conflito", length = 30, updatable = false)
    private MotivoConflito motivoConflito;

    @Transient
    private boolean novo = true;

    protected Subponto() {
    }

    /**
     * Cria o registro. O {@code recebidoEm} é definido aqui, com o relógio do servidor; {@code motivoConflito}
     * nulo significa sem conflito.
     */
    public Subponto(UUID id, Aplicacao aplicacao, BigDecimal latitude, BigDecimal longitude, Instant realizadoEm,
            MotivoConflito motivoConflito) {
        this.id = id;
        this.tenant = aplicacao.getTenant();
        this.aplicacao = aplicacao;
        this.latitude = latitude;
        this.longitude = longitude;
        this.realizadoEm = realizadoEm;
        this.recebidoEm = Instant.now();
        this.conflito = motivoConflito != null;
        this.motivoConflito = motivoConflito;
    }

    @PostLoad
    @PostPersist
    void marcarComoExistente() {
        this.novo = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return novo;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Aplicacao getAplicacao() {
        return aplicacao;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public Instant getRealizadoEm() {
        return realizadoEm;
    }

    public Instant getRecebidoEm() {
        return recebidoEm;
    }

    public boolean isConflito() {
        return conflito;
    }

    public MotivoConflito getMotivoConflito() {
        return motivoConflito;
    }

    @Override
    public String toString() {
        return "Subponto[id=" + id + ", conflito=" + conflito + "]";
    }
}
