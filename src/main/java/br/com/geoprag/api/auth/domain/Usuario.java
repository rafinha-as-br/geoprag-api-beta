package br.com.geoprag.api.auth.domain;

import java.time.Instant;
import java.util.Locale;
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

/**
 * Usuário da API (administrador, sub-administrador ou aplicador).
 *
 * <p>Guarda somente o hash BCrypt da senha. O CPF e o hash são sigilosos: o {@link #toString()} não os inclui
 * e esta entidade nunca deve ser serializada como resposta (use DTOs).
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "nome", length = 150, nullable = false)
    private String nome;

    @Column(name = "email", length = 254, nullable = false)
    private String email;

    @Column(name = "cpf", length = 11, nullable = false)
    private String cpf;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", length = 20, nullable = false)
    private Perfil perfil;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private StatusUsuario status;

    @Column(name = "senha_hash", length = 100, nullable = false)
    private String senhaHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Usuario() {
    }

    /**
     * Cria um usuário ativo. O e-mail é guardado em minúsculas e o CPF só com dígitos.
     *
     * @param senhaHash hash já gerado pelo {@code PasswordEncoder} (BCrypt), nunca a senha em texto
     */
    public Usuario(Tenant tenant, String nome, String email, String cpf, Perfil perfil, String senhaHash) {
        this.tenant = tenant;
        this.nome = nome;
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        this.cpf = Cpf.normalizar(cpf);
        this.perfil = perfil;
        this.status = StatusUsuario.ATIVO;
        this.senhaHash = senhaHash;
    }

    @PrePersist
    void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public void desativar() {
        this.status = StatusUsuario.DESATIVADO;
    }

    public boolean estaAtivo() {
        return status == StatusUsuario.ATIVO;
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    /** Uso interno (ex.: login por CPF). Nunca logar nem devolver em respostas. */
    public String getCpf() {
        return cpf;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public StatusUsuario getStatus() {
        return status;
    }

    /** Hash BCrypt para conferência com {@code PasswordEncoder.matches}. Nunca logar nem devolver em respostas. */
    public String getSenhaHash() {
        return senhaHash;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    /** Sem CPF, e-mail, nome nem hash: só identificadores técnicos. */
    @Override
    public String toString() {
        return "Usuario[id=" + id + ", perfil=" + perfil + ", status=" + status + "]";
    }
}
