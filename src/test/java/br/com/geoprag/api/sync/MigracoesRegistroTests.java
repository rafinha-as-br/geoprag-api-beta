package br.com.geoprag.api.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** A migration V6 aplica do zero e as restrições do banco protegem os dados de campo. */
class MigracoesRegistroTests {

    private static final String SEED = "classpath:db/seed";
    private static final String MIGRATIONS = "classpath:db/migration";
    private static final String PONTO = "00000000-0000-4000-8000-000000000203";
    private static final String DATA = "00000000-0000-4000-8000-000000000402";
    private static final String DATA_OUTRO_PONTO = "00000000-0000-4000-8000-000000000404";
    private static final String PRODUTO = "00000000-0000-4000-8000-000000000501";
    private static final String APLICADOR = "00000000-0000-4000-8000-000000000003";

    private static JdbcTemplate bancoNovo(String nome, boolean comSeed) {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + nome + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        String[] locais = comSeed ? new String[] {MIGRATIONS, SEED} : new String[] {MIGRATIONS};
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(locais).load().migrate();
        return jdbc;
    }

    private static void aplicacao(JdbcTemplate jdbc, String id, String tenant, String ponto, String data,
            String status, String concluidaEm) {
        jdbc.update("""
                insert into aplicacao (id, tenant_id, ponto_id, data_agendada_id, produto_id, aplicador_id, status,
                    concluida_em) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.fromString(id), tenant, UUID.fromString(ponto), UUID.fromString(data),
                UUID.fromString(PRODUTO), UUID.fromString(APLICADOR), status,
                concluidaEm == null ? null : java.time.OffsetDateTime.parse(concluidaEm));
    }

    private static void subponto(JdbcTemplate jdbc, String id, String aplicacao, Object latitude, Object longitude,
            boolean conflito, String motivo) {
        jdbc.update("""
                insert into subponto (id, tenant_id, aplicacao_id, latitude, longitude, realizado_em, recebido_em,
                    conflito, motivo_conflito)
                values (?, 'gaspar-sc', ?, ?, ?, current_timestamp, current_timestamp, ?, ?)
                """, UUID.fromString(id), UUID.fromString(aplicacao), latitude, longitude, conflito, motivo);
    }

    @Test
    void v6AplicaDoZeroSemSeedEAsTabelasFicamVazias() {
        JdbcTemplate jdbc = bancoNovo("registro_sem_seed", false);

        assertThat(jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '6' and \"success\" = true",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from aplicacao", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from subponto", Integer.class)).isZero();
    }

    @Test
    void chavePrimariaDoAppTornaOReenvioIdempotenteNoBanco() {
        JdbcTemplate jdbc = bancoNovo("registro_pk", true);
        String ap = UUID.randomUUID().toString();
        String sub = UUID.randomUUID().toString();
        aplicacao(jdbc, ap, "gaspar-sc", PONTO, DATA, "EM_ANDAMENTO", null);
        subponto(jdbc, sub, ap, -26.9, -48.9, false, null);

        assertThatThrownBy(() -> subponto(jdbc, sub, ap, -26.9, -48.9, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> aplicacao(jdbc, ap, "gaspar-sc", PONTO, DATA, "EM_ANDAMENTO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void conflitoExigeMotivoESemConflitoNaoTemMotivo() {
        JdbcTemplate jdbc = bancoNovo("registro_conflito", true);
        String ap = UUID.randomUUID().toString();
        aplicacao(jdbc, ap, "gaspar-sc", PONTO, DATA, "EM_ANDAMENTO", null);

        assertThatThrownBy(() -> subponto(jdbc, UUID.randomUUID().toString(), ap, 1, 1, true, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> subponto(jdbc, UUID.randomUUID().toString(), ap, 1, 1, false, "PONTO_DESATIVADO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> subponto(jdbc, UUID.randomUUID().toString(), ap, 1, 1, true, "QUALQUER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String motivo : List.of("PONTO_REATRIBUIDO", "PONTO_DESATIVADO", "PONTO_INATIVO", "DATA_JA_ATENDIDA",
                "DATA_CANCELADA")) {
            subponto(jdbc, UUID.randomUUID().toString(), ap, 1, 1, true, motivo);
        }
        assertThat(jdbc.queryForObject("select count(*) from subponto where conflito = true", Integer.class))
                .isEqualTo(5);
    }

    @Test
    void coordenadasForaDaFaixaOuAusentesSaoRecusadas() {
        JdbcTemplate jdbc = bancoNovo("registro_coordenadas", true);
        String ap = UUID.randomUUID().toString();
        aplicacao(jdbc, ap, "gaspar-sc", PONTO, DATA, "EM_ANDAMENTO", null);

        assertThatThrownBy(() -> subponto(jdbc, UUID.randomUUID().toString(), ap, 91, 0, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> subponto(jdbc, UUID.randomUUID().toString(), ap, 0, 181, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> subponto(jdbc, UUID.randomUUID().toString(), ap, null, 0, false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void statusDaAplicacaoEConsistenteComAConclusao() {
        JdbcTemplate jdbc = bancoNovo("registro_status", true);

        assertThatThrownBy(() -> aplicacao(jdbc, UUID.randomUUID().toString(), "gaspar-sc", PONTO, DATA,
                "CONCLUIDA", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> aplicacao(jdbc, UUID.randomUUID().toString(), "gaspar-sc", PONTO, DATA,
                "EM_ANDAMENTO", "2026-10-12T10:00:00-03:00")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> aplicacao(jdbc, UUID.randomUUID().toString(), "gaspar-sc", PONTO, DATA, "FEITA",
                null)).isInstanceOf(DataIntegrityViolationException.class);
        aplicacao(jdbc, UUID.randomUUID().toString(), "gaspar-sc", PONTO, DATA, "CONCLUIDA",
                "2026-10-12T10:00:00-03:00");
    }

    @Test
    void chavesCompostasImpedemMisturarTenantsEPontosInexistentes() {
        JdbcTemplate jdbc = bancoNovo("registro_tenants", true);
        jdbc.update("insert into tenant (id, nome) values ('outro-sc', 'Outro')");

        // ponto, data, produto e aplicador pertencem a gaspar-sc: com tenant_id de outro tenant o banco recusa
        assertThatThrownBy(() -> aplicacao(jdbc, UUID.randomUUID().toString(), "outro-sc", PONTO, DATA,
                "EM_ANDAMENTO", null)).isInstanceOf(DataIntegrityViolationException.class);
        // ponto inexistente
        assertThatThrownBy(() -> aplicacao(jdbc, UUID.randomUUID().toString(), "gaspar-sc",
                UUID.randomUUID().toString(), DATA, "EM_ANDAMENTO", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // subponto de uma aplicação em outro tenant
        String ap = UUID.randomUUID().toString();
        aplicacao(jdbc, ap, "gaspar-sc", PONTO, DATA, "EM_ANDAMENTO", null);
        assertThatThrownBy(() -> jdbc.update("""
                insert into subponto (id, tenant_id, aplicacao_id, latitude, longitude, realizado_em, recebido_em)
                values (random_uuid(), 'outro-sc', ?, 1, 1, current_timestamp, current_timestamp)
                """, UUID.fromString(ap))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aDataAgendadaDeOutroPontoNaoEImpedidaPeloBancoMasOServicoValida() {
        // O banco garante o tenant; que a data pertença ao ponto da aplicação é regra do serviço (testada nele).
        JdbcTemplate jdbc = bancoNovo("registro_data_outro_ponto", true);

        aplicacao(jdbc, UUID.randomUUID().toString(), "gaspar-sc", PONTO, DATA_OUTRO_PONTO, "EM_ANDAMENTO", null);

        assertThat(jdbc.queryForObject("select count(*) from aplicacao", Integer.class)).isEqualTo(1);
    }
}
