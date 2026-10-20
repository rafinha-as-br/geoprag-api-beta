package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** As migrations de ponto aplicam do zero (banco novo) e o seed fictício só entra com a localização {@code db/seed}. */
class MigracoesPontoTests {

    private static final String SEED = "classpath:db/seed";
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final String ARQUIVO_SEED = "db/seed/R__20_seed_bairros_pontos_e_agendamentos_ficticios.sql";

    private static JdbcTemplate bancoNovo(String nome) {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + nome + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    private static void migrar(JdbcTemplate jdbc, String... locations) {
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(locations).load().migrate();
    }

    @Test
    void migrationV3AplicaDoZeroSemSeedECriaAsQuatroTabelasVazias() {
        JdbcTemplate jdbc = bancoNovo("ponto_sem_seed");

        migrar(jdbc, MIGRATIONS);

        assertThat(jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '3' and \"success\" = true",
                Integer.class)).isEqualTo(1);
        for (String tabela : List.of("bairro", "ponto", "agendamento", "data_agendada")) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabela, Integer.class))
                    .as(tabela).isZero();
        }
    }

    @Test
    void seedCarregaBairrosPontosEmTodosOsEstadosEAgendamentos() {
        JdbcTemplate jdbc = bancoNovo("ponto_com_seed");

        migrar(jdbc, MIGRATIONS, SEED);

        assertThat(jdbc.queryForList("select nome from bairro order by nome", String.class))
                .containsExactly("Bela Vista", "Centro", "Margem do Rio");
        assertThat(jdbc.queryForList("select estado from ponto", String.class))
                .containsExactlyInAnyOrder("ENDERECADA", "DIRECIONADA", "ATIVA", "ATIVA", "INATIVA", "DESATIVADO");
        assertThat(jdbc.queryForObject("select count(*) from agendamento", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("select distinct status from data_agendada order by status", String.class))
                .containsExactly("CANCELADA", "CONCLUIDA", "PENDENTE");
        assertThat(jdbc.queryForObject("select count(*) from ponto where tenant_id <> 'gaspar-sc'", Integer.class))
                .isZero();
    }

    /** Referência fixa ("hoje" na data em que o seed foi escrito): o seed usa datas fixas, não relativas. */
    private static final LocalDate HOJE_DE_REFERENCIA = LocalDate.of(2026, 10, 20);

    @Test
    void seedTemPontoComDataPendenteEmAtrasoAlemDaTolerancia() {
        JdbcTemplate jdbc = bancoNovo("ponto_atraso");
        migrar(jdbc, MIGRATIONS, SEED);

        List<Map<String, Object>> pendentes = jdbc.queryForList("""
                select p.identificador, p.tolerancia_atraso_dias, d.data_prevista
                from ponto p
                join agendamento a on a.ponto_id = p.id and a.tenant_id = p.tenant_id
                join data_agendada d on d.agendamento_id = a.id and d.tenant_id = a.tenant_id
                where d.status = 'PENDENTE' and a.vigente = true
                """);

        List<Object> emAtraso = pendentes.stream()
                .filter(l -> paraLocalDate(l.get("data_prevista"))
                        .plusDays(((Number) l.get("tolerancia_atraso_dias")).longValue())
                        .isBefore(HOJE_DE_REFERENCIA))
                .map(l -> l.get("identificador"))
                .distinct()
                .toList();
        assertThat(emAtraso).containsExactly("PA-003");
        assertThat(pendentes).extracting(l -> l.get("identificador")).contains("PA-004");
    }

    @Test
    void seedNaoTemCoordenadasNemDadosReais() {
        JdbcTemplate jdbc = bancoNovo("ponto_sem_dados_reais");
        migrar(jdbc, MIGRATIONS, SEED);

        assertThat(jdbc.queryForObject("select count(*) from ponto where latitude is not null or longitude is not null",
                Integer.class)).isZero();
        // Aplicadores atribuídos são as contas fictícias do seed de usuários (e-mails @example.com).
        assertThat(jdbc.queryForList("""
                select distinct u.email from ponto p join usuario u on u.id = p.aplicador_id
                """, String.class)).isNotEmpty().allMatch(email -> email.endsWith("@example.com"));
    }

    @Test
    void seedEhIdempotente() {
        JdbcTemplate jdbc = bancoNovo("ponto_seed_idempotente");
        migrar(jdbc, MIGRATIONS, SEED);
        Map<String, Integer> antes = contagens(jdbc);

        new ResourceDatabasePopulator(new ClassPathResource(ARQUIVO_SEED)).execute(jdbc.getDataSource());

        assertThat(contagens(jdbc)).isEqualTo(antes);
        assertThat(antes).containsEntry("bairro", 3).containsEntry("ponto", 6)
                .containsEntry("agendamento", 3).containsEntry("data_agendada", 8);
    }

    private static LocalDate paraLocalDate(Object valor) {
        return valor instanceof java.sql.Date data ? data.toLocalDate() : (LocalDate) valor;
    }

    private static Map<String, Integer> contagens(JdbcTemplate jdbc) {
        return Map.of(
                "bairro", jdbc.queryForObject("select count(*) from bairro", Integer.class),
                "ponto", jdbc.queryForObject("select count(*) from ponto", Integer.class),
                "agendamento", jdbc.queryForObject("select count(*) from agendamento", Integer.class),
                "data_agendada", jdbc.queryForObject("select count(*) from data_agendada", Integer.class));
    }
}
