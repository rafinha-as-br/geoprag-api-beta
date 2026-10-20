package br.com.geoprag.api.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Códigos de erro estáveis da API. O {@code code} faz parte do contrato com os clientes:
 * não renomear um valor existente.
 */
public enum ErrorCode {

    REQUISICAO_INVALIDA("Requisição inválida",
            "Não foi possível processar a requisição. Verifique os dados enviados e tente novamente."),
    VALIDACAO_INVALIDA("Dados inválidos",
            "Há campos inválidos. Corrija os dados informados e tente novamente."),
    RECURSO_NAO_ENCONTRADO("Recurso não encontrado",
            "O recurso solicitado não foi encontrado."),
    METODO_NAO_PERMITIDO("Método não permitido",
            "Esta operação não é permitida para o recurso solicitado."),
    FORMATO_NAO_SUPORTADO("Formato não suportado",
            "O formato dos dados enviados não é suportado."),
    ERRO_INTERNO("Erro interno",
            "Ocorreu um erro inesperado. Tente novamente em instantes."),
    CREDENCIAIS_INVALIDAS("Credenciais inválidas",
            "CPF, e-mail ou senha incorretos. Verifique os dados e tente novamente."),
    USUARIO_DESATIVADO("Usuário desativado",
            "O acesso deste usuário está desativado. Procure o administrador."),
    NAO_AUTENTICADO("Não autenticado",
            "Sessão ausente, inválida ou expirada. Entre novamente."),
    ACESSO_NEGADO("Acesso negado",
            "Seu perfil não tem permissão para acessar este recurso.");

    private final String title;
    private final String detail;

    ErrorCode(String title, String detail) {
        this.title = title;
        this.detail = detail;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    /** Código para um erro de framework que não tem tratamento específico, a partir do status HTTP. */
    public static ErrorCode fromStatus(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return RECURSO_NAO_ENCONTRADO;
        }
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return METODO_NAO_PERMITIDO;
        }
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return FORMATO_NAO_SUPORTADO;
        }
        return status.is5xxServerError() ? ERRO_INTERNO : REQUISICAO_INVALIDA;
    }
}
