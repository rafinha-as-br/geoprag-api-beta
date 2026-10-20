/**
 * Feature {@code auth}: login, renovação de sessão e usuário autenticado.
 *
 * <p>Estrutura interna: {@code controller} → {@code service} → {@code repository}
 * (Spring Data JPA), com {@code dto} separado das entidades. As entidades (tenant e usuário) ficam em
 * {@code domain}, os repositórios em {@code repository} e a codificação de senha (BCrypt) em {@code config}.
 */
package br.com.geoprag.api.auth;
