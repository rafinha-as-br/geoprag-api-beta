/**
 * Feature {@code produto}: produto mínimo do beta (um único produto pré-cadastrado por tenant, selecionado pelo
 * aplicador ao registrar uma aplicação).
 *
 * <p>Estrutura interna: {@code domain} (entidade) e {@code repository} (Spring Data). Ainda não há endpoint,
 * CRUD, lote nem estoque: o produto chega ao app pelo pull de sincronização e é referenciado pelo registro de
 * aplicação.
 */
package br.com.geoprag.api.produto;
