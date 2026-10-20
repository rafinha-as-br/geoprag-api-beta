package br.com.geoprag.api.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento global de erros em {@code application/problem+json} (RFC 7807).
 * Toda resposta de erro carrega {@code code} estável e {@code detail} em pt-BR;
 * erros de validação trazem também {@code errors[]} por campo.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return validationResponse(ex, camposInvalidos(ex.getBindingResult().getAllErrors()), headers, status, request);
    }

    /** Validação de parâmetros de método ({@code @RequestParam}, {@code @PathVariable} com constraints). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<CampoInvalido> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result instanceof ParameterErrors parameterErrors
                        ? camposInvalidos(parameterErrors.getAllErrors()).stream()
                        : result.getResolvableErrors().stream().map(error -> new CampoInvalido(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return validationResponse(ex, errors, headers, status, request);
    }

    /** Padroniza as demais exceções de framework (404, 405, 415, JSON ilegível etc.). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            ErrorCode code = ErrorCode.fromStatus(statusCode);
            problem.setTitle(code.title());
            problem.setDetail(code.detail());
            problem.setProperty("code", code.name());
        }
        return response;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ResponseEntity.BodyBuilder resposta = ResponseEntity.status(ex.getStatus());
        if (ex.getCode() == ErrorCode.NAO_AUTENTICADO) {
            resposta.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return resposta.body(ProblemaPadrao.de(ex.getCode(), ex.getStatus()));
    }

    /** Autorização por papel ({@code @PreAuthorize}) que falha dentro do controller. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAcessoNegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ProblemaPadrao.de(ErrorCode.ACESSO_NEGADO, HttpStatus.FORBIDDEN));
    }

    /** Falta de autenticação detectada dentro do controller (a barreira principal é o filtro de segurança). */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleNaoAutenticado(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(ProblemaPadrao.de(ErrorCode.NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleErroInesperado(Exception ex) {
        log.error("Erro inesperado não tratado", ex);
        return ProblemaPadrao.de(ErrorCode.ERRO_INTERNO, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Object> validationResponse(Exception ex, List<CampoInvalido> errors, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemaPadrao.de(ErrorCode.VALIDACAO_INVALIDA, status);
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Erros de campo usam o nome do campo; erros globais do objeto usam o nome do objeto. */
    private static List<CampoInvalido> camposInvalidos(List<? extends ObjectError> errors) {
        return errors.stream()
                .map(error -> new CampoInvalido(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
    }
}
