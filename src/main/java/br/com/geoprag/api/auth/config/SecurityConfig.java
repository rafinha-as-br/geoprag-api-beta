package br.com.geoprag.api.auth.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import br.com.geoprag.api.auth.domain.Perfil;

/**
 * Segurança da API: JWT HS256 no header {@code Authorization: Bearer}, sem sessão e sem cookies (por isso sem CSRF).
 * Tudo exige autenticação, exceto o login, a saúde e a documentação. O perfil do token vira o papel
 * ({@code ROLE_ADMINISTRADOR}, {@code ROLE_SUB_ADMINISTRADOR} ou {@code ROLE_APLICADOR}) e o tenant vem do claim
 * {@code tenantId}, nunca de parâmetro da requisição.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@EnableMethodSecurity
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig {

    static final String CLAIM_TENANT = "tenantId";
    static final String CLAIM_PERFIL = "perfil";

    @Bean
    SecurityFilterChain filtroDeSeguranca(HttpSecurity http, ProblemaSegurancaHandler problemas,
            JwtAuthenticationConverter conversor) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(problemas)
                        .accessDeniedHandler(problemas))
                .oauth2ResourceServer(recurso -> recurso
                        .authenticationEntryPoint(problemas)
                        .accessDeniedHandler(problemas)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversor)));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter conversorDeJwt() {
        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName(CLAIM_PERFIL);
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);
        return conversor;
    }

    @Bean
    SecretKey chaveJwt(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey chaveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chaveJwt));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey chaveJwt) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chaveJwt).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), exigeTenantEPerfilValidos()));
        return decoder;
    }

    /** Token assinado por nós mas sem tenant ou com perfil desconhecido não autentica ninguém. */
    private static OAuth2TokenValidator<Jwt> exigeTenantEPerfilValidos() {
        OAuth2Error erro = new OAuth2Error("invalid_token", "Token sem tenant ou perfil válido.", null);
        return jwt -> {
            String tenant = jwt.getClaimAsString(CLAIM_TENANT);
            String perfil = jwt.getClaimAsString(CLAIM_PERFIL);
            boolean perfilValido = perfil != null
                    && Arrays.stream(Perfil.values()).anyMatch(p -> p.name().equals(perfil));
            return tenant != null && !tenant.isBlank() && perfilValido
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(erro);
        };
    }
}
