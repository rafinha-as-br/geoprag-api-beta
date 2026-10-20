package br.com.geoprag.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.geoprag.api.auth.domain.Cpf;

class CpfTests {

    @Test
    void removeMascaraEMantemSomenteDigitos() {
        assertThat(Cpf.normalizar("111.111.111-11")).isEqualTo("11111111111");
        assertThat(Cpf.normalizar("22222222222")).isEqualTo("22222222222");
    }

    @Test
    void naoExigeDigitoVerificadorValido() {
        assertThat(Cpf.normalizar("00000000000")).isEqualTo("00000000000");
    }

    @Test
    void rejeitaTamanhoInvalidoSemExporOValor() {
        assertThatThrownBy(() -> Cpf.normalizar("1234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("1234567");
        assertThatThrownBy(() -> Cpf.normalizar(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
