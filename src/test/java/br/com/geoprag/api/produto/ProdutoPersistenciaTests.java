package br.com.geoprag.api.produto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.domain.Tenant;
import br.com.geoprag.api.auth.repository.TenantRepository;
import br.com.geoprag.api.produto.domain.Produto;
import br.com.geoprag.api.produto.repository.ProdutoRepository;

/** Contexto completo (perfil test: migrations + seed, {@code ddl-auto=validate}) sobre o produto mínimo. */
@TesteDeIntegracao
@Transactional
class ProdutoPersistenciaTests {

    private static final String GASPAR = "gaspar-sc";
    private static final String OUTRO = "outro-sc";
    private static final UUID PRODUTO_DO_SEED = UUID.fromString("00000000-0000-4000-8000-000000000501");

    @Autowired
    private TenantRepository tenants;
    @Autowired
    private ProdutoRepository produtos;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private Tenant outroTenant() {
        return tenants.saveAndFlush(new Tenant(OUTRO, "Outro (SC)"));
    }

    @Test
    void produtoDoSeedPodeSerLido() {
        Produto produto = produtos.findByTenantIdAndId(GASPAR, PRODUTO_DO_SEED).orElseThrow();

        assertThat(produto.getNome()).isEqualTo("Larvicida (exemplo)");
        assertThat(produto.getTenant().getId()).isEqualTo(GASPAR);
        assertThat(produto.getCriadoEm()).isNotNull();
        assertThat(produtos.findByTenantIdOrderByNome(GASPAR)).extracting(Produto::getId)
                .containsExactly(PRODUTO_DO_SEED);
    }

    @Test
    void produtoNovoGanhaIdDoServidorEEhPersistido() {
        Produto salvo = produtos.save(new Produto(tenants.findById(GASPAR).orElseThrow(), "Outro produto (exemplo)"));
        entityManager.flush();
        entityManager.clear();

        Produto lido = produtos.findByTenantIdAndId(GASPAR, salvo.getId()).orElseThrow();
        assertThat(lido.getId()).isNotNull().isNotEqualTo(PRODUTO_DO_SEED);
        assertThat(lido.getNome()).isEqualTo("Outro produto (exemplo)");
    }

    @Test
    void consultaEhRestritaAoTenantDoToken() {
        Tenant outro = outroTenant();
        Produto produtoOutro = produtos.save(new Produto(outro, "Produto de outro tenant"));
        entityManager.flush();

        assertThat(produtos.findByTenantIdAndId(GASPAR, produtoOutro.getId())).isEmpty();
        assertThat(produtos.findByTenantIdAndId(OUTRO, produtoOutro.getId())).isPresent();
        assertThat(produtos.findByTenantIdAndId(OUTRO, PRODUTO_DO_SEED)).isEmpty();
        assertThat(produtos.findByTenantIdOrderByNome(GASPAR)).extracting(Produto::getNome)
                .containsExactly("Larvicida (exemplo)");
        assertThat(produtos.findByTenantIdOrderByNome(OUTRO)).extracting(Produto::getNome)
                .containsExactly("Produto de outro tenant");
    }

    @Test
    void nomeEUnicoDentroDoTenant() {
        Tenant gaspar = tenants.findById(GASPAR).orElseThrow();

        // O flush direto no EntityManager não passa pela tradução de exceções do Spring Data.
        assertThatThrownBy(() -> {
            produtos.save(new Produto(gaspar, "Larvicida (exemplo)"));
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void mesmoNomePodeExistirEmOutroTenant() {
        Tenant outro = outroTenant();

        produtos.save(new Produto(outro, "Larvicida (exemplo)"));
        entityManager.flush();

        assertThat(produtos.findByTenantIdOrderByNome(OUTRO)).extracting(Produto::getNome)
                .containsExactly("Larvicida (exemplo)");
        assertThat(produtos.findByTenantIdOrderByNome(GASPAR)).hasSize(1);
    }

    @Test
    void bancoRecusaNomeEmBrancoETenantInexistente() {
        String insert = "insert into produto (id, tenant_id, nome) values (random_uuid(), ?, ?)";

        assertThatThrownBy(() -> jdbc.update(insert, GASPAR, "   ")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, GASPAR, "")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "nao-existe", "Produto")).isInstanceOf(
                DataIntegrityViolationException.class);
        jdbc.update(insert, GASPAR, "Produto válido");
    }

    @Test
    void produtoTemChaveUnicaComTenantParaServirDeAlvoDeChavesCompostas() {
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where lower(table_name) = 'produto' and lower(constraint_name) = 'uk_produto_id_tenant'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void repositorioExigeTenantEnaoExpoeFindByIdFindAllNemDelete() {
        assertThat(CrudRepository.class.isAssignableFrom(ProdutoRepository.class))
                .as("não deve herdar findById/findAll/delete sem tenant").isFalse();
        List<Method> metodos = List.of(ProdutoRepository.class.getDeclaredMethods());

        assertThat(metodos).isNotEmpty();
        for (Method metodo : metodos) {
            assertThat(metodo.getName()).as(metodo.toString()).doesNotStartWith("delete").doesNotStartWith("remove");
            if (!metodo.getName().equals("save")) {
                assertThat(metodo.getName()).as(metodo.toString()).startsWith("findByTenantId");
                assertThat(metodo.getParameterTypes()[0]).as(metodo.toString()).isEqualTo(String.class);
            }
        }
    }
}
