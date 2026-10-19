package br.com.geoprag.api.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints existentes só nos testes, para exercitar erro padronizado e CORS. Não faz parte da API. */
@RestController
@RequestMapping("/teste")
class ControllerDeTeste {

    record Pedido(@NotBlank String nome, @Min(1) int idade) {
    }

    @PostMapping("/validacao")
    Pedido validar(@Valid @RequestBody Pedido pedido) {
        return pedido;
    }

    @GetMapping("/parametro")
    String parametro(@RequestParam @Min(1) int pagina) {
        return "ok";
    }

    @GetMapping("/erro-inesperado")
    void erroInesperado() {
        throw new IllegalStateException("detalhe interno sensível");
    }
}
