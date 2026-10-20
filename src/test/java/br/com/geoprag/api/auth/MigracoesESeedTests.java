package br.com.geoprag.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Migrations aplicam do zero (banco novo) e o seed fictício só entra quando a localização {@code db/seed} é usada. */
class MigracoesESeedTests {

    private static final String SEED = "classpath:db/seed";
    private static final String MIGRATIONS = "classpath:db/migration";

    private static JdbcTemplate bancoNovo(String nome) {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + nome + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    private static void migrar(JdbcTemplate jdbc, String... locations) {
        Flyway.configure()
                .dataSource(jdbc.getDataSource())
                .locations(locations)
                .load()
                .migrate();
    }

    @Test
    void migrationsAplicamDoZeroSemSeed() {
        JdbcTemplate jdbc = bancoNovo("migracoes_sem_seed");

        migrar(jdbc, MIGRATIONS);

        assertThat(jdbc.queryForObject("select count(*) from tenant", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '2' and \"success\" = true",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void seedCarregaTenantDeGasparEContasFicticias() {
        JdbcTemplate jdbc = bancoNovo("migracoes_com_seed");

        migrar(jdbc, MIGRATIONS, SEED);

        assertThat(jdbc.queryForObject("select count(*) from tenant where id = 'gaspar-sc'", Integer.class)).isEqualTo(1);
        List<Map<String, Object>> perfis = jdbc.queryForList(
                "select perfil, status, count(*) as total from usuario group by perfil, status order by perfil, status");
        assertThat(perfis).extracting(l -> l.get("perfil") + "/" + l.get("status") + "=" + l.get("total"))
                .containsExactly(
                        "ADMINISTRADOR/ATIVO=1",
                        "APLICADOR/ATIVO=2",
                        "APLICADOR/DESATIVADO=1",
                        "SUB_ADMINISTRADOR/ATIVO=1");
        assertThat(jdbc.queryForList("select email from usuario", String.class))
                .allMatch(email -> email.endsWith("@example.com"));
        // CPFs sabidamente invalidos: digitos repetidos.
        assertThat(jdbc.queryForList("select cpf from usuario", String.class))
                .allMatch(cpf -> cpf.matches("(\\d)\\1{10}"));
    }

    @Test
    void seedEhIdempotente() {
        JdbcTemplate jdbc = bancoNovo("migracoes_seed_idempotente");
        migrar(jdbc, MIGRATIONS, SEED);

        new ResourceDatabasePopulator(
                new ClassPathResource("db/seed/R__10_seed_tenant_gaspar_e_usuarios_ficticios.sql"))
                .execute(jdbc.getDataSource());

        assertThat(jdbc.queryForObject("select count(*) from tenant", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isEqualTo(5);
    }

    @Test
    void seedSoEstaConfiguradoNosPerfisLocalETest() throws Exception {
        assertThat(localizacoesFlyway("application.yml")).isEqualTo(MIGRATIONS);
        assertThat(localizacoesFlyway("application-local.yml")).isEqualTo(MIGRATIONS + "," + SEED);
        assertThat(localizacoesFlyway("application-test.yml")).isEqualTo(MIGRATIONS + "," + SEED);
    }

    private static Object localizacoesFlyway(String arquivo) throws Exception {
        List<PropertySource<?>> fontes = new YamlPropertySourceLoader().load(arquivo, new ClassPathResource(arquivo));
        return fontes.get(0).getProperty("spring.flyway.locations");
    }
}
