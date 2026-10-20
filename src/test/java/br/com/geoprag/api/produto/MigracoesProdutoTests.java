package br.com.geoprag.api.produto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** A migration do produto aplica do zero (banco novo) e o seed fictício só entra com a localização {@code db/seed}. */
class MigracoesProdutoTests {

    private static final String SEED = "classpath:db/seed";
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final String ARQUIVO_SEED = "db/seed/R__30_seed_produto_ficticio.sql";

    private static JdbcTemplate bancoNovo(String nome) {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + nome + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    private static void migrar(JdbcTemplate jdbc, String... locations) {
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(locations).load().migrate();
    }

    @Test
    void migrationV4AplicaDoZeroSemSeedEATabelaFicaVazia() {
        JdbcTemplate jdbc = bancoNovo("produto_sem_seed");

        migrar(jdbc, MIGRATIONS);

        assertThat(jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '4' and \"success\" = true",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from produto", Integer.class)).isZero();
    }

    @Test
    void seedCarregaUmUnicoProdutoFicticioNoTenantDeGaspar() {
        JdbcTemplate jdbc = bancoNovo("produto_com_seed");

        migrar(jdbc, MIGRATIONS, SEED);

        assertThat(jdbc.queryForList("select tenant_id || '/' || nome from produto", String.class))
                .containsExactly("gaspar-sc/Larvicida (exemplo)");
    }

    @Test
    void seedDoProdutoEIdempotente() {
        JdbcTemplate jdbc = bancoNovo("produto_seed_idempotente");
        migrar(jdbc, MIGRATIONS, SEED);

        new ResourceDatabasePopulator(new ClassPathResource(ARQUIVO_SEED)).execute(jdbc.getDataSource());

        assertThat(jdbc.queryForObject("select count(*) from produto", Integer.class)).isEqualTo(1);
    }

    @Test
    void seedDoProdutoRodaDepoisDoSeedDeTenantEUsuario() {
        // R__10 (tenant) vem antes de R__30 (produto) na ordem do Flyway: o produto só existe porque o tenant existe.
        JdbcTemplate jdbc = bancoNovo("produto_ordem_do_seed");
        migrar(jdbc, MIGRATIONS, SEED);

        // Só a ordem relativa: outros seeds (ex.: pontos, R__20) podem existir entre os dois.
        List<String> repetiveis = jdbc.queryForList("""
                select "script" from "flyway_schema_history" where "type" = 'SQL' and "version" is null
                order by "installed_rank"
                """, String.class);
        assertThat(repetiveis).contains("R__10_seed_tenant_gaspar_e_usuarios_ficticios.sql",
                "R__30_seed_produto_ficticio.sql");
        assertThat(repetiveis.indexOf("R__10_seed_tenant_gaspar_e_usuarios_ficticios.sql"))
                .isLessThan(repetiveis.indexOf("R__30_seed_produto_ficticio.sql"));
    }
}
