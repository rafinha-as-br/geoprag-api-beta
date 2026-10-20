package br.com.geoprag.api.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.Tenant;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.common.error.ApiException;
import br.com.geoprag.api.common.error.ErrorCode;
import br.com.geoprag.api.ponto.domain.Bairro;
import br.com.geoprag.api.ponto.domain.EstadoPonto;
import br.com.geoprag.api.ponto.domain.Ponto;

/**
 * Matriz estado × operação do ponto (RN, decisão de Rafinha, 2026-10-19), sem Spring: todas as combinações,
 * permitidas e proibidas. Recusa é {@code 409 OPERACAO_NAO_PERMITIDA} e não altera o ponto.
 */
class MatrizDoPontoTests {

    private static final Tenant TENANT = new Tenant("gaspar-sc", "Gaspar");
    private static final Usuario APLICADOR_A = new Usuario(TENANT, "A", "a@example.com", "11111111111",
            Perfil.APLICADOR, "hash");
    private static final Usuario APLICADOR_B = new Usuario(TENANT, "B", "b@example.com", "22222222222",
            Perfil.APLICADOR, "hash");

    /** Ponto no estado pedido, com aplicador A quando o estado o exige ou o mantém. */
    private static Ponto ponto(EstadoPonto estado) {
        Ponto ponto = new Ponto(TENANT, "PA-001", "Ponto", new Bairro(TENANT, "Centro"), "Rua", BigDecimal.TEN,
                BigDecimal.TEN, 5, estado == EstadoPonto.ENDERECADA ? null : APLICADOR_A);
        ReflectionTestUtils.setField(ponto, "estado", estado);
        return ponto;
    }

    private static void assertRecusa(Runnable operacao, Ponto ponto, EstadoPonto estadoEsperado,
            Usuario aplicadorEsperado) {
        assertThatThrownBy(operacao::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.OPERACAO_NAO_PERMITIDA);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getDetail()).isNotBlank();
        });
        assertThat(ponto.getEstado()).isEqualTo(estadoEsperado);
        assertThat(ponto.getAplicador()).isSameAs(aplicadorEsperado);
        assertThat(ponto.getEstadoAnterior()).isNull();
    }

    // ---- atribuir / trocar ----------------------------------------------------------------------------------

    @Test
    void atribuirEmEnderecadaVaiADirecionada() {
        Ponto ponto = ponto(EstadoPonto.ENDERECADA);

        ponto.atribuirAplicador(APLICADOR_B);

        assertThat(ponto.getEstado()).isEqualTo(EstadoPonto.DIRECIONADA);
        assertThat(ponto.getEstadoAnterior()).isEqualTo(EstadoPonto.ENDERECADA);
        assertThat(ponto.getAplicador()).isSameAs(APLICADOR_B);
    }

    @Test
    void trocarEmDirecionadaMantemOEstadoEOEstadoAnterior() {
        Ponto ponto = ponto(EstadoPonto.DIRECIONADA);

        ponto.atribuirAplicador(APLICADOR_B);

        assertThat(ponto.getEstado()).isEqualTo(EstadoPonto.DIRECIONADA);
        assertThat(ponto.getEstadoAnterior()).isNull();
        assertThat(ponto.getAplicador()).isSameAs(APLICADOR_B);
    }

    @Test
    void trocarEmInativaMantemOEstado() {
        Ponto ponto = ponto(EstadoPonto.INATIVA);

        ponto.atribuirAplicador(APLICADOR_B);

        assertThat(ponto.getEstado()).isEqualTo(EstadoPonto.INATIVA);
        assertThat(ponto.getEstadoAnterior()).isNull();
        assertThat(ponto.getAplicador()).isSameAs(APLICADOR_B);
    }

    @Test
    void atribuirOMesmoAplicadorNaoMudaNada() {
        Ponto ponto = ponto(EstadoPonto.DIRECIONADA);

        ponto.atribuirAplicador(APLICADOR_A);

        assertThat(ponto.getEstado()).isEqualTo(EstadoPonto.DIRECIONADA);
        assertThat(ponto.getAplicador()).isSameAs(APLICADOR_A);
        assertThat(ponto.getEstadoAnterior()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPonto.class, names = { "ATIVA", "DESATIVADO" })
    void atribuirEmAtivaOuDesativadoEhRecusado(EstadoPonto estado) {
        Ponto ponto = ponto(estado);

        assertRecusa(() -> ponto.atribuirAplicador(APLICADOR_B), ponto, estado, APLICADOR_A);
    }

    // ---- remover --------------------------------------------------------------------------------------------

    @Test
    void removerEmDirecionadaVoltaAEnderecada() {
        Ponto ponto = ponto(EstadoPonto.DIRECIONADA);

        ponto.removerAplicador();

        assertThat(ponto.getEstado()).isEqualTo(EstadoPonto.ENDERECADA);
        assertThat(ponto.getEstadoAnterior()).isEqualTo(EstadoPonto.DIRECIONADA);
        assertThat(ponto.getAplicador()).isNull();
    }

    @Test
    void removerEmEnderecadaEhRecusado() {
        Ponto ponto = ponto(EstadoPonto.ENDERECADA);

        assertRecusa(ponto::removerAplicador, ponto, EstadoPonto.ENDERECADA, null);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPonto.class, names = { "ATIVA", "INATIVA", "DESATIVADO" })
    void removerEmAtivaInativaOuDesativadoEhRecusadoEOPontoMantemOAplicador(EstadoPonto estado) {
        Ponto ponto = ponto(estado);

        assertRecusa(ponto::removerAplicador, ponto, estado, APLICADOR_A);
    }

    // ---- ativar ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = EstadoPonto.class, names = { "DIRECIONADA", "INATIVA" })
    void ativarEmDirecionadaOuInativaVaiAAtiva(EstadoPonto estado) {
        Ponto ponto = ponto(estado);

        ponto.ativar();

        assertThat(ponto.getEstado()).isEqualTo(EstadoPonto.ATIVA);
        assertThat(ponto.getEstadoAnterior()).isEqualTo(estado);
        assertThat(ponto.getAplicador()).isSameAs(APLICADOR_A);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPonto.class, names = { "ENDERECADA", "ATIVA", "DESATIVADO" })
    void ativarEmEnderecadaAtivaOuDesativadoEhRecusado(EstadoPonto estado) {
        Ponto ponto = ponto(estado);
        Usuario aplicadorEsperado = ponto.getAplicador();

        assertRecusa(ponto::ativar, ponto, estado, aplicadorEsperado);
        assertRecusa(ponto::verificarPodeAtivar, ponto, estado, aplicadorEsperado);
    }

    @Test
    void ativarSemAplicadorEhRecusadoMesmoSeOEstadoPermitir() {
        Ponto ponto = ponto(EstadoPonto.INATIVA);
        ReflectionTestUtils.setField(ponto, "aplicador", null);

        assertRecusa(ponto::ativar, ponto, EstadoPonto.INATIVA, null);
    }

    @Test
    void asMensagensDeRecusaExplicamOQueFazer() {
        assertThatThrownBy(() -> ponto(EstadoPonto.ATIVA).atribuirAplicador(APLICADOR_B))
                .hasMessage(ErrorCode.OPERACAO_NAO_PERMITIDA.name())
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getDetail()).contains("cancele a aplicação"));
        assertThatThrownBy(() -> ponto(EstadoPonto.ENDERECADA).ativar())
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getDetail()).contains("aplicador"));
    }
}
