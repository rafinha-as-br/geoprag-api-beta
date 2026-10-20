-- SEED DE DESENVOLVIMENTO: carregado SOMENTE nos perfis `local` e `test` (ver README).
-- Todos os dados abaixo sao FICTICIOS. O repositorio e publico: nao coloque dados reais aqui.
--  * CPFs sabidamente invalidos (digitos repetidos): o login nao depende do digito verificador.
--  * E-mails em example.com.
--  * Senha de TODAS as contas: a senha de seed documentada no README (so para uso local);
--    abaixo fica apenas o hash BCrypt.
-- Migration repetivel e idempotente (nao duplica nem sobrescreve registros ja existentes).

INSERT INTO tenant (id, nome)
SELECT 'gaspar-sc', 'Gaspar (SC)'
WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE id = 'gaspar-sc');

INSERT INTO usuario (id, tenant_id, nome, email, cpf, perfil, status, senha_hash)
SELECT '00000000-0000-4000-8000-000000000001', 'gaspar-sc', 'Administrador Exemplo', 'admin@example.com',
       '11111111111', 'ADMINISTRADOR', 'ATIVO', '$2a$10$1LNYSxUHGZB0SrQH.IA5a.pqyYUpNh1L3StBYlnrYooBTSVCbnvOq'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE id = '00000000-0000-4000-8000-000000000001');

INSERT INTO usuario (id, tenant_id, nome, email, cpf, perfil, status, senha_hash)
SELECT '00000000-0000-4000-8000-000000000002', 'gaspar-sc', 'Sub Administrador Exemplo', 'subadmin@example.com',
       '22222222222', 'SUB_ADMINISTRADOR', 'ATIVO', '$2a$10$1LNYSxUHGZB0SrQH.IA5a.pqyYUpNh1L3StBYlnrYooBTSVCbnvOq'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE id = '00000000-0000-4000-8000-000000000002');

INSERT INTO usuario (id, tenant_id, nome, email, cpf, perfil, status, senha_hash)
SELECT '00000000-0000-4000-8000-000000000003', 'gaspar-sc', 'Aplicador Exemplo Um', 'aplicador1@example.com',
       '33333333333', 'APLICADOR', 'ATIVO', '$2a$10$1LNYSxUHGZB0SrQH.IA5a.pqyYUpNh1L3StBYlnrYooBTSVCbnvOq'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE id = '00000000-0000-4000-8000-000000000003');

INSERT INTO usuario (id, tenant_id, nome, email, cpf, perfil, status, senha_hash)
SELECT '00000000-0000-4000-8000-000000000004', 'gaspar-sc', 'Aplicador Exemplo Dois', 'aplicador2@example.com',
       '44444444444', 'APLICADOR', 'ATIVO', '$2a$10$1LNYSxUHGZB0SrQH.IA5a.pqyYUpNh1L3StBYlnrYooBTSVCbnvOq'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE id = '00000000-0000-4000-8000-000000000004');

INSERT INTO usuario (id, tenant_id, nome, email, cpf, perfil, status, senha_hash)
SELECT '00000000-0000-4000-8000-000000000005', 'gaspar-sc', 'Aplicador Exemplo Desativado', 'aplicador.desativado@example.com',
       '55555555555', 'APLICADOR', 'DESATIVADO', '$2a$10$1LNYSxUHGZB0SrQH.IA5a.pqyYUpNh1L3StBYlnrYooBTSVCbnvOq'
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE id = '00000000-0000-4000-8000-000000000005');
