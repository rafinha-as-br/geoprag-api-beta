package br.com.geoprag.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TesteDeIntegracao
class GeopragApiApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextoSobe() {
        assertThat(jdbcTemplate).isNotNull();
    }

    @Test
    void flywayExecutaMigrationInicialV1() {
        Integer aplicadas = jdbcTemplate.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '1' and \"success\" = true",
                Integer.class);

        assertThat(aplicadas).isEqualTo(1);
    }
}
