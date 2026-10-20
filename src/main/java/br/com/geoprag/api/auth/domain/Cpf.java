package br.com.geoprag.api.auth.domain;

import java.util.regex.Pattern;

/**
 * Normalização do CPF para armazenamento: texto com somente os 11 dígitos, sem máscara.
 *
 * <p>Não valida os dígitos verificadores de propósito: o seed usa CPFs sabidamente inválidos e o login por CPF
 * não depende deles. O CPF é dado pessoal: nunca deve ir para log nem para resposta da API.
 */
public final class Cpf {

    private static final Pattern NAO_DIGITO = Pattern.compile("\\D");
    private static final int TAMANHO = 11;

    private Cpf() {
    }

    /** Remove a máscara e exige exatamente 11 dígitos. A mensagem de erro não inclui o valor informado. */
    public static String normalizar(String cpf) {
        if (cpf == null) {
            throw new IllegalArgumentException("CPF é obrigatório");
        }
        String digitos = NAO_DIGITO.matcher(cpf).replaceAll("");
        if (digitos.length() != TAMANHO) {
            throw new IllegalArgumentException("CPF deve ter 11 dígitos");
        }
        return digitos;
    }
}
