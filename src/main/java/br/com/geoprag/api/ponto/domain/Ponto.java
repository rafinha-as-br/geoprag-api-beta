package br.com.geoprag.api.ponto.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.common.error.ApiException;

/**
 * Ponto de aplicação. Nunca é excluído, apenas desativado. O cadastro não tem coordenadas: {@code latitude} e
 * {@code longitude} ficam nulas e só a primeira aplicação em campo as define.
 *
 * <p>Esta entidade só representa os dados; as regras de transição de estado ficam no serviço do servidor
 * (autoridade sobre o estado).
 */
@Entity
@Table(name = "ponto")
public class Ponto {

    /** Tolerância de atraso, em dias corridos, quando o cadastro não informa. */
    public static final int TOLERANCIA_ATRASO_PADRAO_DIAS = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private Tenant tenant;

    @Column(name = "identificador", length = 20, nullable = false)
    private String identificador;

    @Column(name = "nome", length = 150, nullable = false)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bairro_id", nullable = false)
    private Bairro bairro;

    @Column(name = "endereco", length = 200, nullable = false)
    private String endereco;

    @Column(name = "numero_referencia", length = 30)
    private String numeroReferencia;

    @Column(name = "descricao_do_trecho", length = 500)
    private String descricaoDoTrecho;

    @Column(name = "largura_metros", precision = 8, scale = 2)
    private BigDecimal larguraMetros;

    @Column(name = "profundidade_metros", precision = 8, scale = 2)
    private BigDecimal profundidadeMetros;

    @Column(name = "velocidade_metros_por_segundo", precision = 8, scale = 2)
    private BigDecimal velocidadeMetrosPorSegundo;

    @Column(name = "dosagem_ml", precision = 8, scale = 2, nullable = false)
    private BigDecimal dosagemMl;

    @Column(name = "distancia_entre_subpontos_metros", precision = 8, scale = 2, nullable = false)
    private BigDecimal distanciaEntreSubpontosMetros;

    @Column(name = "quantidade_de_subpontos", nullable = false)
    private int quantidadeDeSubpontos;

    @Column(name = "tolerancia_atraso_dias", nullable = false)
    private int toleranciaAtrasoDias = TOLERANCIA_ATRASO_PADRAO_DIAS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aplicador_id")
    private Usuario aplicador;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 20, nullable = false)
    private EstadoPonto estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoPonto estadoAnterior;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Ponto() {
    }

    /**
     * Cria um ponto com os dados obrigatórios do cadastro. Nasce {@code ENDERECADA} (sem aplicador) ou
     * {@code DIRECIONADA} (com aplicador), com a tolerância de atraso padrão.
     */
    public Ponto(Tenant tenant, String identificador, String nome, Bairro bairro, String endereco,
            BigDecimal dosagemMl, BigDecimal distanciaEntreSubpontosMetros, int quantidadeDeSubpontos,
            Usuario aplicador) {
        this.tenant = tenant;
        this.identificador = identificador;
        this.nome = nome;
        this.bairro = bairro;
        this.endereco = endereco;
        this.dosagemMl = dosagemMl;
        this.distanciaEntreSubpontosMetros = distanciaEntreSubpontosMetros;
        this.quantidadeDeSubpontos = quantidadeDeSubpontos;
        this.aplicador = aplicador;
        this.estado = aplicador == null ? EstadoPonto.ENDERECADA : EstadoPonto.DIRECIONADA;
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

    /**
     * Atribui ou troca o aplicador (matriz da RN): {@code ENDERECADA} vai a {@code DIRECIONADA}; {@code DIRECIONADA}
     * e {@code INATIVA} trocam sem mudar de estado; {@code ATIVA} e {@code DESATIVADO} são recusados (409).
     */
    public void atribuirAplicador(Usuario novoAplicador) {
        switch (estado) {
            case ENDERECADA -> {
                this.aplicador = novoAplicador;
                mudarEstado(EstadoPonto.DIRECIONADA);
            }
            case DIRECIONADA, INATIVA -> this.aplicador = novoAplicador;
            case ATIVA -> throw ApiException.operacaoNaoPermitida(
                    "Para trocar o aplicador de um ponto ativo, cancele a aplicação antes.");
            case DESATIVADO -> throw ApiException.operacaoNaoPermitida(
                    "Ponto desativado não aceita aplicador. Reative o ponto antes.");
        }
    }

    /** Remove o aplicador: só {@code DIRECIONADA} (volta a {@code ENDERECADA}); os demais estados são recusados. */
    public void removerAplicador() {
        switch (estado) {
            case DIRECIONADA -> {
                this.aplicador = null;
                mudarEstado(EstadoPonto.ENDERECADA);
            }
            case ENDERECADA -> throw ApiException.operacaoNaoPermitida("O ponto não tem aplicador para remover.");
            case ATIVA -> throw ApiException.operacaoNaoPermitida(
                    "Para remover o aplicador de um ponto ativo, cancele a aplicação antes.");
            case INATIVA -> throw ApiException.operacaoNaoPermitida(
                    "O aplicador de um ponto inativo é mantido até um novo agendamento.");
            case DESATIVADO -> throw ApiException.operacaoNaoPermitida(
                    "Ponto desativado não aceita alteração de aplicador.");
        }
    }

    /** Recusa (409) se o estado atual não permite ativar; não altera nada. */
    public void verificarPodeAtivar() {
        switch (estado) {
            case DIRECIONADA, INATIVA -> {
                if (aplicador == null) {
                    throw ApiException.operacaoNaoPermitida("O ponto precisa de um aplicador antes de ser ativado.");
                }
            }
            case ENDERECADA -> throw ApiException.operacaoNaoPermitida(
                    "O ponto precisa de um aplicador antes de ser ativado.");
            case ATIVA -> throw ApiException.operacaoNaoPermitida("O ponto já está ativo.");
            case DESATIVADO -> throw ApiException.operacaoNaoPermitida(
                    "Ponto desativado não pode ser ativado. Reative o ponto antes.");
        }
    }

    /** Ativa o ponto ({@code DIRECIONADA} ou {@code INATIVA} com aplicador); o agendamento é do serviço. */
    public void ativar() {
        verificarPodeAtivar();
        mudarEstado(EstadoPonto.ATIVA);
    }

    /** Desativa o ponto (remoção lógica): guarda o estado em que estava em {@code estadoAnterior}. */
    public void desativar() {
        if (estado == EstadoPonto.DESATIVADO) {
            throw ApiException.operacaoNaoPermitida("O ponto já está desativado.");
        }
        mudarEstado(EstadoPonto.DESATIVADO);
    }

    /** Reativa um ponto desativado, devolvendo-o ao estado que tinha antes da desativação. */
    public void reativar() {
        if (estado != EstadoPonto.DESATIVADO) {
            throw ApiException.operacaoNaoPermitida("Só é possível reativar um ponto desativado.");
        }
        if (estadoAnterior == null || estadoAnterior == EstadoPonto.DESATIVADO) {
            throw ApiException.operacaoNaoPermitida("Não há estado anterior para restaurar neste ponto.");
        }
        if ((estadoAnterior == EstadoPonto.DIRECIONADA || estadoAnterior == EstadoPonto.ATIVA) && aplicador == null) {
            throw ApiException.operacaoNaoPermitida("O ponto precisa de um aplicador para voltar ao estado anterior.");
        }
        mudarEstado(estadoAnterior);
    }

    /** Cancela a aplicação química do ponto: só {@code ATIVA}, que vai a {@code INATIVA}. */
    public void cancelar() {
        if (estado != EstadoPonto.ATIVA) {
            throw ApiException.operacaoNaoPermitida("Só é possível cancelar a aplicação de um ponto ativo.");
        }
        mudarEstado(EstadoPonto.INATIVA);
    }

    /** Dados de cadastro do ponto (o que o administrador informa ao criar ou editar). */
    public record Cadastro(String nome, Bairro bairro, String endereco, String numeroReferencia,
            String descricaoDoTrecho, BigDecimal larguraMetros, BigDecimal profundidadeMetros,
            BigDecimal velocidadeMetrosPorSegundo, BigDecimal dosagemMl, BigDecimal distanciaEntreSubpontosMetros,
            int quantidadeDeSubpontos, int toleranciaAtrasoDias) {
    }

    /** Se o cadastro novo altera algo além de {@code nome} e {@code toleranciaAtrasoDias} (sempre editáveis). */
    public boolean alteraCadastroCompleto(Cadastro novo) {
        return !bairro.getId().equals(novo.bairro().getId())
                || !Objects.equals(endereco, novo.endereco())
                || !Objects.equals(numeroReferencia, novo.numeroReferencia())
                || !Objects.equals(descricaoDoTrecho, novo.descricaoDoTrecho())
                || !numericamenteIguais(larguraMetros, novo.larguraMetros())
                || !numericamenteIguais(profundidadeMetros, novo.profundidadeMetros())
                || !numericamenteIguais(velocidadeMetrosPorSegundo, novo.velocidadeMetrosPorSegundo())
                || !numericamenteIguais(dosagemMl, novo.dosagemMl())
                || !numericamenteIguais(distanciaEntreSubpontosMetros, novo.distanciaEntreSubpontosMetros())
                || quantidadeDeSubpontos != novo.quantidadeDeSubpontos();
    }

    /** Campos que o cadastro novo muda, no formato {@code campo: antes -> depois} (para a trilha de auditoria). */
    public List<String> diferencas(Cadastro novo) {
        List<String> diferencas = new ArrayList<>();
        diferenca(diferencas, "nome", nome, novo.nome());
        diferenca(diferencas, "bairroId", bairro.getId(), novo.bairro().getId());
        diferenca(diferencas, "endereco", endereco, novo.endereco());
        diferenca(diferencas, "numeroReferencia", numeroReferencia, novo.numeroReferencia());
        diferenca(diferencas, "descricaoDoTrecho", descricaoDoTrecho, novo.descricaoDoTrecho());
        diferencaNumerica(diferencas, "larguraMetros", larguraMetros, novo.larguraMetros());
        diferencaNumerica(diferencas, "profundidadeMetros", profundidadeMetros, novo.profundidadeMetros());
        diferencaNumerica(diferencas, "velocidadeMetrosPorSegundo", velocidadeMetrosPorSegundo,
                novo.velocidadeMetrosPorSegundo());
        diferencaNumerica(diferencas, "dosagemMl", dosagemMl, novo.dosagemMl());
        diferencaNumerica(diferencas, "distanciaEntreSubpontosMetros", distanciaEntreSubpontosMetros,
                novo.distanciaEntreSubpontosMetros());
        diferenca(diferencas, "quantidadeDeSubpontos", quantidadeDeSubpontos, novo.quantidadeDeSubpontos());
        diferenca(diferencas, "toleranciaAtrasoDias", toleranciaAtrasoDias, novo.toleranciaAtrasoDias());
        return diferencas;
    }

    private static void diferenca(List<String> diferencas, String campo, Object antes, Object depois) {
        if (!Objects.equals(antes, depois)) {
            diferencas.add(campo + ": " + texto(antes) + " -> " + texto(depois));
        }
    }

    private static void diferencaNumerica(List<String> diferencas, String campo, BigDecimal antes,
            BigDecimal depois) {
        if (!numericamenteIguais(antes, depois)) {
            diferencas.add(campo + ": " + texto(antes) + " -> " + texto(depois));
        }
    }

    private static String texto(Object valor) {
        if (valor == null) {
            return "(vazio)";
        }
        return valor instanceof BigDecimal numero ? numero.toPlainString() : valor.toString();
    }

    /** Substitui o cadastro; o aplicador, o estado e o identificador não mudam por aqui. */
    public void aplicarCadastro(Cadastro novo) {
        this.nome = novo.nome();
        this.bairro = novo.bairro();
        this.endereco = novo.endereco();
        this.numeroReferencia = novo.numeroReferencia();
        this.descricaoDoTrecho = novo.descricaoDoTrecho();
        this.larguraMetros = novo.larguraMetros();
        this.profundidadeMetros = novo.profundidadeMetros();
        this.velocidadeMetrosPorSegundo = novo.velocidadeMetrosPorSegundo();
        this.dosagemMl = novo.dosagemMl();
        this.distanciaEntreSubpontosMetros = novo.distanciaEntreSubpontosMetros();
        this.quantidadeDeSubpontos = novo.quantidadeDeSubpontos();
        this.toleranciaAtrasoDias = novo.toleranciaAtrasoDias();
    }

    private static boolean numericamenteIguais(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a == b : a.compareTo(b) == 0;
    }

    /** {@code estadoAnterior} guarda o estado antes da última transição; troca de aplicador não é transição. */
    private void mudarEstado(EstadoPonto novo) {
        if (novo != estado) {
            this.estadoAnterior = estado;
            this.estado = novo;
        }
    }

    public UUID getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getIdentificador() {
        return identificador;
    }

    public String getNome() {
        return nome;
    }

    public Bairro getBairro() {
        return bairro;
    }

    public String getEndereco() {
        return endereco;
    }

    public String getNumeroReferencia() {
        return numeroReferencia;
    }

    public String getDescricaoDoTrecho() {
        return descricaoDoTrecho;
    }

    public BigDecimal getLarguraMetros() {
        return larguraMetros;
    }

    public BigDecimal getProfundidadeMetros() {
        return profundidadeMetros;
    }

    public BigDecimal getVelocidadeMetrosPorSegundo() {
        return velocidadeMetrosPorSegundo;
    }

    public BigDecimal getDosagemMl() {
        return dosagemMl;
    }

    public BigDecimal getDistanciaEntreSubpontosMetros() {
        return distanciaEntreSubpontosMetros;
    }

    public int getQuantidadeDeSubpontos() {
        return quantidadeDeSubpontos;
    }

    public int getToleranciaAtrasoDias() {
        return toleranciaAtrasoDias;
    }

    public Usuario getAplicador() {
        return aplicador;
    }

    public EstadoPonto getEstado() {
        return estado;
    }

    public EstadoPonto getEstadoAnterior() {
        return estadoAnterior;
    }

    /** Nula até a primeira aplicação em campo. */
    public BigDecimal getLatitude() {
        return latitude;
    }

    /** Nula até a primeira aplicação em campo. */
    public BigDecimal getLongitude() {
        return longitude;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    @Override
    public String toString() {
        return "Ponto[id=" + id + ", estado=" + estado + "]";
    }
}
