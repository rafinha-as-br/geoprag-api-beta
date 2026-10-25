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
import jakarta.persistence.PreUpdate;
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

    /** Quando o administrador removeu o alerta de atraso desta recorrência; nulo se não removeu. */
    @Column(name = "alerta_removido_em")
    private Instant alertaRemovidoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected DataAgendada() {
    }

    /** Cria uma data pendente. */
    public DataAgendada(Tenant tenant, Agendamento agendamento, LocalDate dataPrevista) {
        this.tenant = tenant;
        this.agendamento = agendamento;
        this.dataPrevista = dataPrevista;
        this.status = StatusDataAgendada.PENDENTE;
    }

    /** Marca a última alteração (usada pelo pull incremental para saber o que mudou). */
    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    @PrePersist
    void aoPersistir() {
        atualizadoEm = Instant.now();
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    /** Dá baixa na data (recorrência atendida). Só uma data pendente pode ser baixada; devolve se baixou. */
    public boolean baixar() {
        if (status != StatusDataAgendada.PENDENTE) {
            return false;
        }
        this.status = StatusDataAgendada.CONCLUIDA;
        return true;
    }

    /** Cancela a data se ainda estiver pendente; concluída ou já cancelada permanece como está. */
    public boolean cancelar() {
        if (status != StatusDataAgendada.PENDENTE) {
            return false;
        }
        this.status = StatusDataAgendada.CANCELADA;
        return true;
    }

    /** Remove o alerta de atraso desta data; vale só para ela, a próxima recorrência que atrasar alerta de novo. */
    public void removerAlerta(Instant quando) {
        this.alertaRemovidoEm = quando;
    }

    public Instant getAlertaRemovidoEm() {
        return alertaRemovidoEm;
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

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public String toString() {
        return "DataAgendada[id=" + id + ", status=" + status + "]";
    }
}
