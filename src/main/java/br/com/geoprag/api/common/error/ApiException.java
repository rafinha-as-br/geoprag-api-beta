package br.com.geoprag.api.common.error;

import org.springframework.http.HttpStatus;

/**
 * Erro de negócio com {@link ErrorCode} estável e status HTTP, convertido em {@code problem+json}
 * pelo {@link ApiExceptionHandler}. A mensagem é a do código: nunca inclui dados do pedido (ex.: CPF).
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;

    public ApiException(ErrorCode code, HttpStatus status) {
        super(code.name());
        this.code = code;
        this.status = status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
