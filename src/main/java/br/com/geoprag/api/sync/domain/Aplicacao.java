package br.com.geoprag.api.sync.domain;

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
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.ponto.domain.DataAgendada;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.produto.domain.Produto;

/**
 * Agrupa os subpontos de uma mesma aplicação feita em campo. O {@code id} é o UUID gerado no app. Concluída pelo
 * sistema quando atinge a quantidade de subpontos do ponto.
 */
@Entity
@Table(name = "aplicacao")
public class Aplicacao implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ponto_id", nullable = false, updatable = false)
    private Ponto ponto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_agendada_id", nullable = false, updatable = false)
    private DataAgendada dataAgendada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id", nullable = false, updatable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aplicador_id", nullable = false, updatable = false)
    private Usuario aplicador;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusAplicacao status = StatusAplicacao.EM_ANDAMENTO;

    @Column(name = "concluida_em")
    private Instant concluidaEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm = Instant.now();

    @Transient
    private boolean nova = true;

    protected Aplicacao() {
    }

    public Aplicacao(UUID id, Ponto ponto, DataAgendada dataAgendada, Produto produto, Usuario aplicador) {
        this.id = id;
        this.tenant = ponto.getTenant();
        this.ponto = ponto;
        this.dataAgendada = dataAgendada;
        this.produto = produto;
        this.aplicador = aplicador;
    }

    /** Conclui a aplicação (a única mudança permitida depois de criada). */
    public void concluir(Instant quando) {
        this.status = StatusAplicacao.CONCLUIDA;
        this.concluidaEm = quando;
    }

    @PostLoad
    @PostPersist
    void marcarComoExistente() {
        this.nova = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nova;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Ponto getPonto() {
        return ponto;
    }

    public DataAgendada getDataAgendada() {
        return dataAgendada;
    }

    public Produto getProduto() {
        return produto;
    }

    public Usuario getAplicador() {
        return aplicador;
    }

    public StatusAplicacao getStatus() {
        return status;
    }

    public Instant getConcluidaEm() {
        return concluidaEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public String toString() {
        return "Aplicacao[id=" + id + ", status=" + status + "]";
    }
}
