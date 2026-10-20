package br.com.geoprag.api.common.error;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Monta o corpo padrão de erro (RFC 7807) com {@code code} estável e {@code detail} em pt-BR. */
public final class ProblemaPadrao {

    private ProblemaPadrao() {
    }

    public static ProblemDetail de(ErrorCode code, HttpStatusCode status) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, code.detail());
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        return problem;
    }
}
