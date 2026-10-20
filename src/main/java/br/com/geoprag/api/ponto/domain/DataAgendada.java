package br.com.geoprag.api.ponto.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import br.com.geoprag.api.auth.domain.Tenant;

/** Uma das datas geradas pelo servidor para um agendamento; o registro de aplicação a referencia pelo id. */
@Entity
@Table(name = "data_agendada")
public class DataAgendada {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false, updatable = false)
    private Agendamento agendamento;

    /** Data de calendário, no fuso do tenant. */
    @Column(name = "data_prevista", nullable = false)
    private LocalDate dataPrevista;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusDataAgendada status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected DataAgendada() {
    }

    /** Cria uma data pendente. */
    public DataAgendada(Tenant tenant, Agendamento agendamento, LocalDate dataPrevista) {
        this.tenant = tenant;
        this.agendamento = agendamento;
        this.dataPrevista = dataPrevista;
        this.status = StatusDataAgendada.PENDENTE;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public Agendamento getAgendamento() {
        return agendamento;
    }

    public LocalDate getDataPrevista() {
        return dataPrevista;
    }

    public StatusDataAgendada getStatus() {
        return status;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public String toString() {
        return "DataAgendada[id=" + id + ", status=" + status + "]";
    }
}
