package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.TokensDeTeste;

/**
 * Mutações concorrentes do mesmo ponto contra um PostgreSQL real (o {@code SELECT … FOR UPDATE} só se comporta
 * como em produção fora do H2). Sem transação de teste. Precisa de Docker; sem Docker a classe é ignorada.
 */
@Testcontainers(disabledWithoutDocker = true)
@TesteDeIntegracao
class TransicaoConcorrentePostgresTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID PA_002 = UUID.fromString("00000000-0000-4000-8000-000000000202");
    private static final int TENTATIVAS = 6;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokensDeTeste tokens;
    @Autowired
    private Clock relogio;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void restauraOPa002() {
        jdbc.update("delete from data_agendada where agendamento_id in (select id from agendamento where ponto_id = ?)",
                PA_002);
        jdbc.update("delete from agendamento where ponto_id = ?", PA_002);
        jdbc.update("update ponto set estado = 'DIRECIONADA', estado_anterior = null, aplicador_id = ? where id = ?",
                UUID.fromString(TokensDeTeste.ID_APLICADOR), PA_002);
    }

    private List<Integer> emParalelo(Callable<Integer> chamada) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(TENTATIVAS);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Integer>> futuros = new ArrayList<>();
            for (int i = 0; i < TENTATIVAS; i++) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return chamada.call();
                }));
            }
            largada.countDown();
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> futuro : futuros) {
                status.add(futuro.get());
            }
            return status;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void ativacoesSimultaneasDoMesmoPontoTemUmVencedorEUmSoAgendamentoVigente() throws Exception {
        String corpo = "{\"dataInicio\":\"" + LocalDate.now(relogio).plusDays(2)
                + "\",\"intervaloDias\":7,\"quantidadeRecorrencias\":3}";

        List<Integer> status = emParalelo(() -> mockMvc.perform(post("/api/v1/pontos/" + PA_002 + "/ativacao")
                .header(HttpHeaders.AUTHORIZATION, tokens.bearerAdministrador())
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andReturn().getResponse().getStatus());

        assertThat(status).filteredOn(s -> s == 200).hasSize(1);
        assertThat(status).filteredOn(s -> s == 409).hasSize(TENTATIVAS - 1);
        assertThat(jdbc.queryForObject("select count(*) from agendamento where ponto_id = ? and vigente = true",
                Integer.class, PA_002)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from data_agendada where agendamento_id in "
                + "(select id from agendamento where ponto_id = ?)", Integer.class, PA_002)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select estado from ponto where id = ?", String.class, PA_002))
                .isEqualTo("ATIVA");
    }

    @Test
    void atribuicoesSimultaneasDeAplicadoresDiferentesTerminamEmUmEstadoConsistente() throws Exception {
        String[] aplicadores = { "00000000-0000-4000-8000-000000000003", "00000000-0000-4000-8000-000000000004" };
        int[] contador = { 0 };

        List<Integer> status = emParalelo(() -> {
            String aplicador;
            synchronized (contador) {
                aplicador = aplicadores[contador[0]++ % 2];
            }
            return mockMvc.perform(post("/api/v1/pontos/" + PA_002 + "/aplicador")
                    .header(HttpHeaders.AUTHORIZATION, tokens.bearerAdministrador())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"aplicadorId\":\"" + aplicador + "\"}"))
                    .andReturn().getResponse().getStatus();
        });

        assertThat(status).containsOnly(200);
        assertThat(jdbc.queryForObject("select estado from ponto where id = ?", String.class, PA_002))
                .isEqualTo("DIRECIONADA");
        assertThat(jdbc.queryForObject("select count(*) from ponto where id = ? and aplicador_id in (?, ?)",
                Integer.class, PA_002, UUID.fromString(aplicadores[0]), UUID.fromString(aplicadores[1])))
                .isEqualTo(1);
    }
}
