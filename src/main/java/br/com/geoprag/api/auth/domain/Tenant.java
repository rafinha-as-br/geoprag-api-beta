package br.com.geoprag.api.auth.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Município atendido pela API. O {@code id} é o identificador textual do contrato (ex.: {@code gaspar-sc}). */
@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @Column(name = "id", length = 60, nullable = false, updatable = false)
    private String id;

    @Column(name = "nome", length = 150, nullable = false)
    private String nome;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Tenant() {
    }

    public Tenant(String id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public String toString() {
        return "Tenant[id=" + id + "]";
    }
}
