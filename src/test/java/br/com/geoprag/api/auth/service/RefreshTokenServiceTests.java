package br.com.geoprag.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenServiceTests {

    @Test
    void hashEOSha256EmHexadecimalDe64Caracteres() {
        // Vetor de teste conhecido do SHA-256 para "abc".
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(RefreshTokenService.hash("outro valor")).hasSize(64).isNotEqualTo(RefreshTokenService.hash("abc"));
    }

    @Test
    void validadeDoRefreshETrintaDias() {
        assertThat(RefreshTokenService.VALIDADE).hasDays(30);
    }
}
