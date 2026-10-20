package br.com.geoprag.api.ponto.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import br.com.geoprag.api.auth.domain.Tenant;

/**
 * Recorrência de aplicações de um ponto (data de início, intervalo e quantidade). Um ponto reativado ganha um
 * agendamento novo e o anterior fica como histórico ({@code vigente = false}); existir no máximo um vigente por
 * ponto é garantido pelo serviço.
 */
@Entity
@Table(name = "agendamento")
public class Agendamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ponto_id", nullable = false, updatable = false)
    private Ponto ponto;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "intervalo_dias", nullable = false)
    private int intervaloDias;

    @Column(name = "quantidade_recorrencias", nullable = false)
    private int quantidadeRecorrencias;

    @Column(name = "vigente", nullable = false)
    private boolean vigente = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Agendamento() {
    }

    /** Cria um agendamento vigente. */
    public Agendamento(Tenant tenant, Ponto ponto, LocalDate dataInicio, int intervaloDias,
            int quantidadeRecorrencias) {
        this.tenant = tenant;
        this.ponto = ponto;
        this.dataInicio = dataInicio;
        this.intervaloDias = intervaloDias;
        this.quantidadeRecorrencias = quantidadeRecorrencias;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    /** Encerra o agendamento (fica como histórico); só pode haver um vigente por ponto, garantido pelo serviço. */
    public void encerrar() {
        this.vigente = false;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Ponto getPonto() {
        return ponto;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public int getIntervaloDias() {
        return intervaloDias;
    }

    public int getQuantidadeRecorrencias() {
        return quantidadeRecorrencias;
    }

    public boolean isVigente() {
        return vigente;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public String toString() {
        return "Agendamento[id=" + id + ", vigente=" + vigente + "]";
    }
}
