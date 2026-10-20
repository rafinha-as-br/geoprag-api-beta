package br.com.geoprag.api.ponto.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Evento da trilha de auditoria de um ponto (quem mudou o quê e quando). <b>Append-only</b>: sem setters, colunas
 * não atualizáveis e repositório sem {@code delete}. Guarda só identificadores e dados do ponto; nunca CPF, e-mail
 * ou nome de usuário.
 */
@Entity
@Table(name = "ponto_auditoria")
public class PontoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "ponto_id", nullable = false, updatable = false)
    private UUID pontoId;

    @Column(name = "ator_id", nullable = false, updatable = false)
    private UUID atorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "acao", length = 30, nullable = false, updatable = false)
    private AcaoDePonto acao;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20, updatable = false)
    private EstadoPonto estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_novo", length = 20, nullable = false, updatable = false)
    private EstadoPonto estadoNovo;

    @Column(name = "aplicador_anterior_id", updatable = false)
    private UUID aplicadorAnteriorId;

    @Column(name = "aplicador_novo_id", updatable = false)
    private UUID aplicadorNovoId;

    @Column(name = "detalhes", length = 2000, updatable = false)
    private String detalhes;

    @Column(name = "ocorrido_em", nullable = false, updatable = false)
    private Instant ocorridoEm;

    protected PontoAuditoria() {
    }

    public PontoAuditoria(String tenantId, UUID pontoId, UUID atorId, AcaoDePonto acao, EstadoPonto estadoAnterior,
            EstadoPonto estadoNovo, UUID aplicadorAnteriorId, UUID aplicadorNovoId, String detalhes,
            Instant ocorridoEm) {
        this.tenantId = tenantId;
        this.pontoId = pontoId;
        this.atorId = atorId;
        this.acao = acao;
        this.estadoAnterior = estadoAnterior;
        this.estadoNovo = estadoNovo;
        this.aplicadorAnteriorId = aplicadorAnteriorId;
        this.aplicadorNovoId = aplicadorNovoId;
        this.detalhes = detalhes;
        this.ocorridoEm = ocorridoEm;
    }

    @PrePersist
    void aoPersistir() {
        if (ocorridoEm == null) {
            ocorridoEm = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getPontoId() {
        return pontoId;
    }

    public UUID getAtorId() {
        return atorId;
    }

    public AcaoDePonto getAcao() {
        return acao;
    }

    public EstadoPonto getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoPonto getEstadoNovo() {
        return estadoNovo;
    }

    public UUID getAplicadorAnteriorId() {
        return aplicadorAnteriorId;
    }

    public UUID getAplicadorNovoId() {
        return aplicadorNovoId;
    }

    public String getDetalhes() {
        return detalhes;
    }

    public Instant getOcorridoEm() {
        return ocorridoEm;
    }

    @Override
    public String toString() {
        return "PontoAuditoria[id=" + id + ", acao=" + acao + "]";
    }
}
