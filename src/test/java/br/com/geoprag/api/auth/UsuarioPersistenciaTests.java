package br.com.geoprag.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import br.com.geoprag.api.TesteDeIntegracao;
import br.com.geoprag.api.auth.domain.Perfil;
import br.com.geoprag.api.auth.domain.StatusUsuario;
import br.com.geoprag.api.auth.domain.Tenant;
import br.com.geoprag.api.auth.domain.Usuario;
import br.com.geoprag.api.auth.repository.TenantRepository;
import br.com.geoprag.api.auth.repository.UsuarioRepository;

/** Contexto completo (perfil test: migrations + seed, {@code ddl-auto=validate}) sobre tenant e usuário. */
@TesteDeIntegracao
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class UsuarioPersistenciaTests {

    private static final String SENHA_SEED = "Geoprag@Local2026";
    private static final String CPF_NOVO = "98765432100";

    @Autowired
    private TenantRepository tenants;
    @Autowired
    private UsuarioRepository usuarios;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Environment environment;

    private Tenant gaspar() {
        return tenants.findById("gaspar-sc").orElseThrow();
    }

    private Usuario novoUsuario(String email, String cpf) {
        return new Usuario(gaspar(), "Teste Fictício", email, cpf, Perfil.APLICADOR,
                passwordEncoder.encode("senha-de-teste"));
    }

    @Test
    void seedCarregaTenantDeGaspar() {
        Tenant gaspar = gaspar();

        assertThat(gaspar.getNome()).isEqualTo("Gaspar (SC)");
    }

    @Test
    void seedTemTodosOsPerfisEUmAplicadorDesativado() {
        List<Usuario> todos = usuarios.findAll();

        assertThat(todos).extracting(Usuario::getPerfil)
                .containsExactlyInAnyOrder(Perfil.ADMINISTRADOR, Perfil.SUB_ADMINISTRADOR,
                        Perfil.APLICADOR, Perfil.APLICADOR, Perfil.APLICADOR);
        assertThat(usuarios.findByTenantIdAndPerfil("gaspar-sc", Perfil.APLICADOR))
                .extracting(Usuario::getStatus)
                .containsExactlyInAnyOrder(StatusUsuario.ATIVO, StatusUsuario.ATIVO, StatusUsuario.DESATIVADO);
        Usuario desativado = usuarios.findByEmail("aplicador.desativado@example.com").orElseThrow();
        assertThat(desativado.estaAtivo()).isFalse();
    }

    @Test
    void usuarioEncontradoPorEmailOuCpfComTenantTextual() {
        Usuario porEmail = usuarios.findByEmail("admin@example.com").orElseThrow();
        Usuario porCpf = usuarios.findByCpf("11111111111").orElseThrow();

        assertThat(porCpf.getId()).isEqualTo(porEmail.getId());
        assertThat(porEmail.getTenant().getId()).isEqualTo("gaspar-sc");
        assertThat(porEmail.getPerfil()).isEqualTo(Perfil.ADMINISTRADOR);
        assertThat(porEmail.getStatus()).isEqualTo(StatusUsuario.ATIVO);
    }

    @Test
    void senhaDoSeedEstaEmBcryptENuncaEmTexto() {
        for (Usuario usuario : usuarios.findAll()) {
            assertThat(usuario.getSenhaHash()).startsWith("$2").isNotEqualTo(SENHA_SEED);
            assertThat(passwordEncoder.matches(SENHA_SEED, usuario.getSenhaHash())).isTrue();
            assertThat(passwordEncoder.matches("outra-senha", usuario.getSenhaHash())).isFalse();
        }
        assertThat(jdbc.queryForObject("select count(*) from usuario where senha_hash = ?", Integer.class, SENHA_SEED))
                .isZero();
    }

    @Test
    void usuarioNovoGuardaSomenteHashBcrypt() {
        Usuario salvo = usuarios.saveAndFlush(novoUsuario("novo@example.com", CPF_NOVO));

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getSenhaHash()).startsWith("$2").isNotEqualTo("senha-de-teste");
        assertThat(passwordEncoder.matches("senha-de-teste", salvo.getSenhaHash())).isTrue();
        assertThat(salvo.getCriadoEm()).isNotNull();
    }

    @Test
    void emailEhGuardadoEmMinusculasECpfSemMascara() {
        Usuario salvo = usuarios.saveAndFlush(novoUsuario("  Novo.Usuario@Example.COM ", "987.654.321-00"));

        assertThat(salvo.getEmail()).isEqualTo("novo.usuario@example.com");
        assertThat(salvo.getCpf()).isEqualTo(CPF_NOVO);
    }

    @Test
    void cpfEhUnicoMesmoEmOutroTenant() {
        Tenant outro = tenants.saveAndFlush(new Tenant("outro-sc", "Outro (SC)"));
        Usuario duplicado = new Usuario(outro, "Duplicado", "duplicado@example.com", "11111111111",
                Perfil.APLICADOR, passwordEncoder.encode("x"));

        assertThatThrownBy(() -> usuarios.saveAndFlush(duplicado)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emailEhUnicoMesmoComCaixaDiferenteEEmOutroTenant() {
        Tenant outro = tenants.saveAndFlush(new Tenant("outro-sc", "Outro (SC)"));
        Usuario duplicado = new Usuario(outro, "Duplicado", "ADMIN@Example.com", CPF_NOVO,
                Perfil.APLICADOR, passwordEncoder.encode("x"));

        assertThatThrownBy(() -> usuarios.saveAndFlush(duplicado)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoRejeitaPerfilStatusEEmailForaDoPadrao() {
        String insert = "insert into usuario (id, tenant_id, nome, email, cpf, perfil, status, senha_hash) "
                + "values (random_uuid(), 'gaspar-sc', 'X', ?, ?, ?, ?, 'hash')";

        assertThatThrownBy(() -> jdbc.update(insert, "a1@example.com", "10000000001", "ROOT", "ATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "a2@example.com", "10000000002", "APLICADOR", "TALVEZ"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "A3@example.com", "10000000003", "APLICADOR", "ATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "a4@example.com", "123", "APLICADOR", "ATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void toStringENaoExpoemCpfEmailNemHash() {
        Usuario usuario = usuarios.findByEmail("admin@example.com").orElseThrow();

        String texto = usuario.toString() + " " + usuario.getTenant();

        assertThat(texto).doesNotContain(usuario.getCpf())
                .doesNotContain(usuario.getEmail())
                .doesNotContain(usuario.getSenhaHash())
                .doesNotContain("$2a$");
    }

    @Test
    void cpfNaoApareceNosLogsAoSalvarECarregar(CapturedOutput saida) {
        Usuario salvo = usuarios.saveAndFlush(novoUsuario("logs@example.com", CPF_NOVO));
        usuarios.findByCpf(CPF_NOVO);
        usuarios.findById(salvo.getId());

        assertThat(saida.getAll()).doesNotContain(CPF_NOVO).doesNotContain("11111111111");
        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class, false)).isFalse();
        assertThat(environment.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isNull();
    }
}
