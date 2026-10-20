package br.com.geoprag.api.common.time;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação no fuso de operação ({@code APP_FUSO_HORARIO}, padrão {@code America/Sao_Paulo}). O modelo
 * ainda não tem fuso por tenant; datas de calendário (ex.: "hoje" para validar a primeira aplicação) usam este fuso.
 * Um fuso inválido impede a API de subir.
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio(@Value("${app.fuso-horario:America/Sao_Paulo}") String fuso) {
        return Clock.system(ZoneId.of(fuso));
    }
}
