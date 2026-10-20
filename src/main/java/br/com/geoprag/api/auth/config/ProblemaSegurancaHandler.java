package br.com.geoprag.api.auth.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.geoprag.api.common.error.ErrorCode;
import br.com.geoprag.api.common.error.ProblemaPadrao;

/**
 * Respostas de segurança do filtro (antes de chegar ao controller) no mesmo formato {@code problem+json}
 * do restante da API: {@code 401 NAO_AUTENTICADO} e {@code 403 ACESSO_NEGADO}.
 */
@Component
class ProblemaSegurancaHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    ProblemaSegurancaHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        escrever(response, ErrorCode.NAO_AUTENTICADO, HttpStatus.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        escrever(response, ErrorCode.ACESSO_NEGADO, HttpStatus.FORBIDDEN);
    }

    private void escrever(HttpServletResponse response, ErrorCode code, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ProblemaPadrao.de(code, status));
    }
}
