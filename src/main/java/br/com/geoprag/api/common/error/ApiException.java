package br.com.geoprag.api.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Erro de negócio com {@link ErrorCode} estável e status HTTP, convertido em {@code problem+json}
 * pelo {@link ApiExceptionHandler}. A mensagem é a do código ou um {@code detail} fixo em pt-BR escolhido pelo
 * servidor: nunca inclui dados do pedido (ex.: CPF).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final String detail;
    private final List<CampoInvalido> errors;

    public ApiException(ErrorCode code, HttpStatus status) {
        this(code, status, null, List.of());
    }

    private ApiException(ErrorCode code, HttpStatus status, String detail, List<CampoInvalido> errors) {
        super(code.name());
        this.code = code;
        this.status = status;
        this.detail = detail;
        this.errors = errors;
    }

    /** {@code 409 OPERACAO_NAO_PERMITIDA}: o estado atual não permite a operação; {@code motivo} vai no detail. */
    public static ApiException operacaoNaoPermitida(String motivo) {
        return new ApiException(ErrorCode.OPERACAO_NAO_PERMITIDA, HttpStatus.CONFLICT, motivo, List.of());
    }

    /** {@code 400 VALIDACAO_INVALIDA} com um item em {@code errors[]}, para validações que dependem do servidor. */
    public static ApiException validacao(String campo, String mensagem) {
        return new ApiException(ErrorCode.VALIDACAO_INVALIDA, HttpStatus.BAD_REQUEST, null,
                List.of(new CampoInvalido(campo, mensagem)));
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Detail específico em pt-BR, ou {@code null} para usar o do código. */
    public String getDetail() {
        return detail;
    }

    public List<CampoInvalido> getErrors() {
        return errors;
    }
}
