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

/** Desativar, reativar e cancelar (e a comparação de cadastro) em {@link Ponto}, sem Spring. */
class CicloDeVidaDoPontoTests {

    private static final Tenant TENANT = new Tenant("gaspar-sc", "Gaspar");
    private static final Usuario APLICADOR = new Usuario(TENANT, "A", "a@example.com", "11111111111",
            Perfil.APLICADOR, "hash");

    private static Ponto ponto(EstadoPonto estado, EstadoPonto anterior) {
        Ponto ponto = new Ponto(TENANT, "PA-001", "Ponto", new Bairro(TENANT, "Centro"), "Rua", BigDecimal.TEN,
                BigDecimal.TEN, 5, estado == EstadoPonto.ENDERECADA ? null : APLICADOR);
        ReflectionTestUtils.setField(ponto, "estado", estado);
        ReflectionTestUtils.setField(ponto, "estadoAnterior", anterior);
        return ponto;
    }

    private static void assertRecusa(Runnable operacao, Ponto ponto, EstadoPonto estado, EstadoPonto anterior) {
        assertThatThrownBy(operacao::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.OPERACAO_NAO_PERMITIDA);
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getDetail()).isNotBlank();
        });
        assertThat(ponto.getEstado()).isEqualTo(estado);
        assertThat(ponto.getEstadoAnterior()).isEqualTo(anterior);
    }

    // ---- desativar -------------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = EstadoPonto.class, names = { "ENDERECADA", "DIRECIONADA", "ATIVA", "INATIVA" })
    void desativarGuardaOEstadoEmQueOPontoEstava(EstadoPonto estado) {
        Ponto ponto = ponto(estado, null);

        ponto.desativar();

        assertThat(ponto.getEstado()).isEqualTo(EstadoPonto.DESATIVADO);
        assertThat(ponto.getEstadoAnterior()).isEqualTo(estado);
    }

    @Test
    void desativarPontoJaDesativadoEhRecusado() {
        Ponto ponto = ponto(EstadoPonto.DESATIVADO, EstadoPonto.ATIVA);

        assertRecusa(ponto::desativar, ponto, EstadoPonto.DESATIVADO, EstadoPonto.ATIVA);
    }

    // ---- reativar --------------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = EstadoPonto.class, names = { "ENDERECADA", "DIRECIONADA", "ATIVA", "INATIVA" })
    void reativarDevolveOPontoAoEstadoAnterior(EstadoPonto anterior) {
        Ponto ponto = ponto(EstadoPonto.DESATIVADO, anterior);
        if (anterior == EstadoPonto.ENDERECADA) {
            ReflectionTestUtils.setField(ponto, "aplicador", null);
        }

        ponto.reativar();

        assertThat(ponto.getEstado()).isEqualTo(anterior);
        assertThat(ponto.getEstadoAnterior()).isEqualTo(EstadoPonto.DESATIVADO);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPonto.class, names = { "ENDERECADA", "DIRECIONADA", "ATIVA", "INATIVA" })
    void reativarPontoQueNaoEstaDesativadoEhRecusado(EstadoPonto estado) {
        Ponto ponto = ponto(estado, EstadoPonto.ENDERECADA);

        assertRecusa(ponto::reativar, ponto, estado, EstadoPonto.ENDERECADA);
    }

    @Test
    void reativarSemEstadoAnteriorValidoEhRecusado() {
        Ponto semAnterior = ponto(EstadoPonto.DESATIVADO, null);
        Ponto anteriorDesativado = ponto(EstadoPonto.DESATIVADO, EstadoPonto.DESATIVADO);

        assertRecusa(semAnterior::reativar, semAnterior, EstadoPonto.DESATIVADO, null);
        assertRecusa(anteriorDesativado::reativar, anteriorDesativado, EstadoPonto.DESATIVADO,
                EstadoPonto.DESATIVADO);
    }

    @Test
    void reativarParaEstadoQueExigeAplicadorSemAplicadorEhRecusado() {
        Ponto ponto = ponto(EstadoPonto.DESATIVADO, EstadoPonto.ATIVA);
        ReflectionTestUtils.setField(ponto, "aplicador", null);

        assertRecusa(ponto::reativar, ponto, EstadoPonto.DESATIVADO, EstadoPonto.ATIVA);
    }

    // ---- cancelar --------------------------------------------------------------------------------------------

    @Test
    void cancelarPontoAtivoVaiAInativa() {
        Ponto ponto = ponto(EstadoPonto.ATIVA, EstadoPonto.DIRECIONADA);

        ponto.cancelar();

        assertThat(ponto.getEstado()).isEqualTo(EstadoPonto.INATIVA);
        assertThat(ponto.getEstadoAnterior()).isEqualTo(EstadoPonto.ATIVA);
        assertThat(ponto.getAplicador()).isSameAs(APLICADOR);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPonto.class, names = { "ENDERECADA", "DIRECIONADA", "INATIVA", "DESATIVADO" })
    void cancelarForaDeAtivaEhRecusado(EstadoPonto estado) {
        Ponto ponto = ponto(estado, null);

        assertRecusa(ponto::cancelar, ponto, estado, null);
    }

    // ---- comparação do cadastro -------------------------------------------------------------------------------

    @Test
    void soNomeEToleranciaNaoAlteramOCadastroCompleto() {
        Bairro bairro = new Bairro(TENANT, "Centro");
        ReflectionTestUtils.setField(bairro, "id", java.util.UUID.randomUUID());
        Ponto ponto = ponto(EstadoPonto.ENDERECADA, null);
        ReflectionTestUtils.setField(ponto, "bairro", bairro);
        Ponto.Cadastro igual = new Ponto.Cadastro("Outro nome", bairro, "Rua", null, null, null, null, null,
                new BigDecimal("10.00"), new BigDecimal("10"), 5, 9);

        assertThat(ponto.alteraCadastroCompleto(igual)).isFalse();
        assertThat(ponto.alteraCadastroCompleto(new Ponto.Cadastro("Ponto", bairro, "Rua nova", null, null, null,
                null, null, BigDecimal.TEN, BigDecimal.TEN, 5, 4))).isTrue();
        assertThat(ponto.alteraCadastroCompleto(new Ponto.Cadastro("Ponto", bairro, "Rua", null, null, null, null,
                null, BigDecimal.TEN, BigDecimal.TEN, 6, 4))).isTrue();
        assertThat(ponto.alteraCadastroCompleto(new Ponto.Cadastro("Ponto", bairro, "Rua", null, null,
                BigDecimal.ONE, null, null, BigDecimal.TEN, BigDecimal.TEN, 5, 4))).isTrue();
    }
}
