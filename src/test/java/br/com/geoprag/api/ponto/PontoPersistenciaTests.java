package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.Tenant;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.repository.TenantRepository;
import br.com.geoprag.api.auth.repository.UsuarioRepository;
import br.com.geoprag.api.ponto.domain.Agendamento;
import br.com.geoprag.api.ponto.domain.Bairro;
import br.com.geoprag.api.ponto.domain.DataAgendada;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;
import br.com.geoprag.api.ponto.domain.StatusDataAgendada;
import br.com.geoprag.api.ponto.repository.AgendamentoRepository;
import br.com.geoprag.api.ponto.repository.BairroRepository;
import br.com.geoprag.api.ponto.repository.DataAgendadaRepository;
import br.com.geoprag.api.ponto.repository.PontoRepository;

/** Contexto completo (perfil test: migrations + seed, {@code ddl-auto=validate}) sobre bairro, ponto e agenda. */
@TesteDeIntegracao
@Transactional
class PontoPersistenciaTests {

    private static final String GASPAR = "gaspar-sc";
    private static final String OUTRO = "outro-sc";
    private static final UUID APLICADOR_UM = UUID.fromString("00000000-0000-4000-8000-000000000003");
    private static final UUID APLICADOR_DOIS = UUID.fromString("00000000-0000-4000-8000-000000000004");
    private static final UUID BAIRRO_CENTRO = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID PONTO_PA_003 = UUID.fromString("00000000-0000-4000-8000-000000000203");

    @Autowired
    private TenantRepository tenants;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private BairroRepository bairros;
    @Autowired
    private PontoRepository pontos;
    @Autowired
    private AgendamentoRepository agendamentos;
    @Autowired
    private DataAgendadaRepository datas;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private Tenant gaspar() {
        return tenants.findById(GASPAR).orElseThrow();
    }

    private Tenant outroTenant() {
        return tenants.saveAndFlush(new Tenant(OUTRO, "Outro (SC)"));
    }

    private Usuario aplicador(UUID id) {
        return usuarios.findById(id).orElseThrow();
    }

    private Bairro bairroCentro() {
        return bairros.findByTenantIdAndId(GASPAR, BAIRRO_CENTRO).orElseThrow();
    }

    private Ponto novoPonto(String identificador, Usuario aplicador) {
        return pontos.save(new Ponto(gaspar(), identificador, "Ponto " + identificador, bairroCentro(),
                "Rua Fictícia", new BigDecimal("20.00"), new BigDecimal("10.00"), 5, aplicador));
    }

    /** Insere por SQL (sem as proteções da entidade) para exercitar os checks e as chaves do banco. */
    private void inserirPonto(String tenant, String identificador, UUID bairro, int quantidade, UUID aplicador,
            String estado) {
        jdbc.update("""
                insert into ponto (id, tenant_id, identificador, nome, bairro_id, endereco, dosagem_ml,
                    distancia_entre_subpontos_metros, quantidade_de_subpontos, aplicador_id, estado)
                values (random_uuid(), ?, ?, 'Nome', ?, 'Endereco', 20, 10, ?, ?, ?)
                """, tenant, identificador, bairro, quantidade, aplicador, estado);
    }

    // ---- seed e leitura pelos repositórios ---------------------------------------------------------------

    @Test
    void seedPodeSerLidoPelosRepositoriosDoTenant() {
        assertThat(bairros.findByTenantIdOrderByNome(GASPAR)).extracting(Bairro::getNome)
                .containsExactly("Bela Vista", "Centro", "Margem do Rio");
        assertThat(pontos.findByTenantIdOrderByIdentificador(GASPAR)).extracting(Ponto::getIdentificador)
                .containsExactly("PA-001", "PA-002", "PA-003", "PA-004", "PA-005", "PA-006");
        assertThat(pontos.findByTenantIdAndEstadoOrderByIdentificador(GASPAR, EstadoPonto.ATIVA))
                .extracting(Ponto::getIdentificador).containsExactly("PA-003", "PA-004");
        assertThat(pontos.findByTenantIdAndAplicadorIdOrderByIdentificador(GASPAR, APLICADOR_UM))
                .extracting(Ponto::getIdentificador).containsExactly("PA-002", "PA-003", "PA-006");
        assertThat(pontos.findByTenantIdAndAplicadorIdOrderByIdentificador(GASPAR, APLICADOR_DOIS))
                .extracting(Ponto::getIdentificador).containsExactly("PA-004", "PA-005");
    }

    @Test
    void pontoDoSeedTemAgendamentoVigenteComDatasEToleranciaPadrao() {
        Ponto ponto = pontos.findByTenantIdAndId(GASPAR, PONTO_PA_003).orElseThrow();
        Agendamento agendamento = agendamentos.findByTenantIdAndPontoIdAndVigenteTrue(GASPAR, ponto.getId())
                .orElseThrow();
        List<DataAgendada> agenda = datas.findByTenantIdAndAgendamentoIdOrderByDataPrevista(GASPAR,
                agendamento.getId());

        assertThat(ponto.getToleranciaAtrasoDias()).isEqualTo(Ponto.TOLERANCIA_ATRASO_PADRAO_DIAS).isEqualTo(4);
        assertThat(ponto.getEstado()).isEqualTo(EstadoPonto.ATIVA);
        assertThat(ponto.getEstadoAnterior()).isEqualTo(EstadoPonto.DIRECIONADA);
        assertThat(agendamento.getIntervaloDias()).isEqualTo(7);
        assertThat(agenda).extracting(DataAgendada::getDataPrevista)
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19));
        assertThat(agenda).extracting(DataAgendada::getStatus)
                .containsExactly(StatusDataAgendada.CONCLUIDA, StatusDataAgendada.PENDENTE,
                        StatusDataAgendada.PENDENTE);
    }

    @Test
    void pontoInativoMantemOHistoricoDoAgendamentoEncerrado() {
        UUID pa005 = UUID.fromString("00000000-0000-4000-8000-000000000205");

        assertThat(agendamentos.findByTenantIdAndPontoIdAndVigenteTrue(GASPAR, pa005)).isEmpty();
        assertThat(agendamentos.findByTenantIdAndPontoIdOrderByDataInicioDesc(GASPAR, pa005)).hasSize(1);
    }

    // ---- criação pelas entidades -------------------------------------------------------------------------

    @Test
    void pontoSemAplicadorNasceEnderecadaEComAplicadorNasceDirecionada() {
        Ponto semAplicador = novoPonto("PA-901", null);
        Ponto comAplicador = novoPonto("PA-902", aplicador(APLICADOR_UM));
        entityManager.flush();

        assertThat(semAplicador.getEstado()).isEqualTo(EstadoPonto.ENDERECADA);
        assertThat(semAplicador.getAplicador()).isNull();
        assertThat(comAplicador.getEstado()).isEqualTo(EstadoPonto.DIRECIONADA);
        assertThat(comAplicador.getAplicador().getId()).isEqualTo(APLICADOR_UM);
    }

    @Test
    void cadastroNaoTemCoordenadasNemMedidasEUsaToleranciaPadrao() {
        Ponto salvo = novoPonto("PA-903", null);
        entityManager.flush();
        entityManager.clear();

        Ponto lido = pontos.findByTenantIdAndId(GASPAR, salvo.getId()).orElseThrow();
        assertThat(lido.getLatitude()).isNull();
        assertThat(lido.getLongitude()).isNull();
        assertThat(lido.getLarguraMetros()).isNull();
        assertThat(lido.getProfundidadeMetros()).isNull();
        assertThat(lido.getVelocidadeMetrosPorSegundo()).isNull();
        assertThat(lido.getToleranciaAtrasoDias()).isEqualTo(4);
        assertThat(lido.getEstadoAnterior()).isNull();
        assertThat(lido.getCriadoEm()).isNotNull();
    }

    @Test
    void agendamentoNasceVigenteEDataAgendadaNascePendente() {
        Ponto ponto = novoPonto("PA-904", aplicador(APLICADOR_UM));
        Agendamento agendamento = agendamentos.save(
                new Agendamento(gaspar(), ponto, LocalDate.of(2026, 12, 1), 7, 2));
        DataAgendada primeira = datas.save(new DataAgendada(gaspar(), agendamento, LocalDate.of(2026, 12, 1)));
        datas.save(new DataAgendada(gaspar(), agendamento, LocalDate.of(2026, 12, 8)));
        entityManager.flush();

        assertThat(agendamento.isVigente()).isTrue();
        assertThat(primeira.getStatus()).isEqualTo(StatusDataAgendada.PENDENTE);
        assertThat(agendamentos.findByTenantIdAndPontoIdAndVigenteTrue(GASPAR, ponto.getId())).isPresent();
        assertThat(datas.findByTenantIdAndAgendamentoIdOrderByDataPrevista(GASPAR, agendamento.getId()))
                .hasSize(2);
    }

    // ---- isolamento por tenant ----------------------------------------------------------------------------

    @Test
    void consultasNaoEnxergamDadosDeOutroTenant() {
        Tenant outro = outroTenant();
        Bairro bairroOutro = bairros.save(new Bairro(outro, "Centro"));
        Ponto pontoOutro = pontos.save(new Ponto(outro, "PA-001", "Ponto de outro tenant", bairroOutro, "Rua X",
                new BigDecimal("10"), new BigDecimal("5"), 3, null));
        Agendamento agendamentoOutro = agendamentos.save(
                new Agendamento(outro, pontoOutro, LocalDate.of(2026, 12, 1), 7, 1));
        DataAgendada dataOutro = datas.save(new DataAgendada(outro, agendamentoOutro, LocalDate.of(2026, 12, 1)));
        entityManager.flush();

        assertThat(pontos.findByTenantIdAndId(GASPAR, pontoOutro.getId())).isEmpty();
        assertThat(pontos.findByTenantIdAndId(OUTRO, pontoOutro.getId())).isPresent();
        assertThat(pontos.findByTenantIdOrderByIdentificador(GASPAR)).hasSize(6)
                .extracting(Ponto::getNome).doesNotContain("Ponto de outro tenant");
        assertThat(pontos.findByTenantIdOrderByIdentificador(OUTRO)).extracting(Ponto::getId)
                .containsExactly(pontoOutro.getId());
        assertThat(bairros.findByTenantIdAndId(GASPAR, bairroOutro.getId())).isEmpty();
        assertThat(bairros.findByTenantIdOrderByNome(OUTRO)).extracting(Bairro::getId)
                .containsExactly(bairroOutro.getId());
        assertThat(agendamentos.findByTenantIdAndPontoIdAndVigenteTrue(GASPAR, pontoOutro.getId())).isEmpty();
        assertThat(datas.findByTenantIdAndId(GASPAR, dataOutro.getId())).isEmpty();
        assertThat(datas.findByTenantIdAndAgendamentoIdOrderByDataPrevista(GASPAR, agendamentoOutro.getId()))
                .isEmpty();
    }

    @Test
    void identificadorDoPontoEUnicoDentroDoTenantMasPodeRepetirEmOutro() {
        Tenant outro = outroTenant();
        Bairro bairroOutro = bairros.save(new Bairro(outro, "Centro"));
        entityManager.flush();

        assertThatThrownBy(() -> inserirPonto(GASPAR, "PA-001", BAIRRO_CENTRO, 5, null, "ENDERECADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        inserirPonto(OUTRO, "PA-001", bairroOutro.getId(), 5, null, "ENDERECADA");
        assertThat(pontos.findByTenantIdOrderByIdentificador(OUTRO)).hasSize(1);
    }

    @Test
    void bancoRecusaPontoComBairroOuAplicadorDeOutroTenant() {
        Tenant outro = outroTenant();
        Bairro bairroOutro = bairros.save(new Bairro(outro, "Centro"));
        Usuario aplicadorOutro = usuarios.save(new Usuario(outro, "Aplicador Outro", "aplicador.outro@example.com",
                "66666666666", Perfil.APLICADOR, passwordEncoder.encode("x")));
        entityManager.flush();

        // bairro de outro tenant
        assertThatThrownBy(() -> inserirPonto(GASPAR, "PA-950", bairroOutro.getId(), 5, null, "ENDERECADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // aplicador de outro tenant
        assertThatThrownBy(() -> inserirPonto(GASPAR, "PA-951", BAIRRO_CENTRO, 5, aplicadorOutro.getId(),
                "DIRECIONADA")).isInstanceOf(DataIntegrityViolationException.class);
        // o mesmo aplicador no tenant dele funciona
        inserirPonto(OUTRO, "PA-952", bairroOutro.getId(), 5, aplicadorOutro.getId(), "DIRECIONADA");
    }

    @Test
    void bancoRecusaAgendaDePontoOuAgendamentoDeOutroTenant() {
        Tenant outro = outroTenant();
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update("""
                insert into agendamento (id, tenant_id, ponto_id, data_inicio, intervalo_dias, quantidade_recorrencias)
                values (random_uuid(), ?, ?, date '2026-12-01', 7, 1)
                """, outro.getId(), PONTO_PA_003)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
                values (random_uuid(), ?, '00000000-0000-4000-8000-000000000301', date '2026-12-01', 'PENDENTE')
                """, outro.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- checks do banco ------------------------------------------------------------------------------------

    @Test
    void bancoRecusaEstadoForaDoPadraoEIncoerenteComOAplicador() {
        assertThatThrownBy(() -> inserirPonto(GASPAR, "PA-960", BAIRRO_CENTRO, 5, null, "ATRIBUIDO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserirPonto(GASPAR, "PA-961", BAIRRO_CENTRO, 5, APLICADOR_UM, "ENDERECADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserirPonto(GASPAR, "PA-962", BAIRRO_CENTRO, 5, null, "DIRECIONADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserirPonto(GASPAR, "PA-963", BAIRRO_CENTRO, 5, null, "ATIVA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // sem aplicador, inativo e desativado são permitidos
        inserirPonto(GASPAR, "PA-964", BAIRRO_CENTRO, 5, null, "INATIVA");
        inserirPonto(GASPAR, "PA-965", BAIRRO_CENTRO, 5, null, "DESATIVADO");
        assertThatThrownBy(() -> jdbc.update("update ponto set estado_anterior = 'TALVEZ' where id = ?",
                PONTO_PA_003)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoRecusaMedidasToleranciaECoordenadasInvalidas() {
        assertThatThrownBy(() -> inserirPonto(GASPAR, "PA-970", BAIRRO_CENTRO, 0, null, "ENDERECADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update ponto set dosagem_ml = 0 where id = ?", PONTO_PA_003))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update ponto set distancia_entre_subpontos_metros = -1 where id = ?",
                PONTO_PA_003)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update ponto set tolerancia_atraso_dias = -1 where id = ?",
                PONTO_PA_003)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update ponto set largura_metros = 0 where id = ?", PONTO_PA_003))
                .isInstanceOf(DataIntegrityViolationException.class);
        // coordenadas: só as duas juntas e válidas
        assertThatThrownBy(() -> jdbc.update("update ponto set latitude = -26.9 where id = ?", PONTO_PA_003))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update ponto set longitude = -48.9 where id = ?", PONTO_PA_003))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update ponto set latitude = 91, longitude = -48.9 where id = ?",
                PONTO_PA_003)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update ponto set latitude = -26.9, longitude = -48.9 where id = ?", PONTO_PA_003);
        jdbc.update("update ponto set tolerancia_atraso_dias = 0 where id = ?", PONTO_PA_003);
    }

    @Test
    void bancoRecusaAgendamentoEDatasInvalidos() {
        UUID agendamento = UUID.fromString("00000000-0000-4000-8000-000000000301");

        assertThatThrownBy(() -> jdbc.update("""
                insert into agendamento (id, tenant_id, ponto_id, data_inicio, intervalo_dias, quantidade_recorrencias)
                values (random_uuid(), ?, ?, date '2026-12-01', 0, 1)
                """, GASPAR, PONTO_PA_003)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into agendamento (id, tenant_id, ponto_id, data_inicio, intervalo_dias, quantidade_recorrencias)
                values (random_uuid(), ?, ?, date '2026-12-01', 7, 0)
                """, GASPAR, PONTO_PA_003)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
                values (random_uuid(), ?, ?, date '2026-12-01', 'FEITA')
                """, GASPAR, agendamento)).isInstanceOf(DataIntegrityViolationException.class);
        // mesma data duas vezes no mesmo agendamento
        assertThatThrownBy(() -> jdbc.update("""
                insert into data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
                values (random_uuid(), ?, ?, date '2026-10-12', 'PENDENTE')
                """, GASPAR, agendamento)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- estrutura dos repositórios --------------------------------------------------------------------------

    @Test
    void todaConsultaDosRepositoriosRecebeOTenantEPontoNuncaEExcluido() {
        List<Class<?>> repositorios = List.of(BairroRepository.class, PontoRepository.class,
                AgendamentoRepository.class, DataAgendadaRepository.class);

        for (Class<?> repositorio : repositorios) {
            assertThat(CrudRepository.class.isAssignableFrom(repositorio))
                    .as(repositorio.getSimpleName() + " não deve herdar findById/findAll/delete sem tenant").isFalse();
            for (Method metodo : repositorio.getDeclaredMethods()) {
                assertThat(metodo.getName()).as(metodo.toString()).doesNotStartWith("delete")
                        .doesNotStartWith("remove");
                if (!metodo.getName().equals("save")) {
                    assertThat(metodo.getName()).as(metodo.toString()).startsWith("findByTenantId");
                    assertThat(Arrays.asList(metodo.getParameterTypes()).get(0)).as(metodo.toString())
                            .isEqualTo(String.class);
                }
            }
        }
    }
}
