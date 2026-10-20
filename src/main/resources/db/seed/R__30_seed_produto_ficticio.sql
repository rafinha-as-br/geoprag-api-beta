-- SEED DE DESENVOLVIMENTO: carregado SOMENTE nos perfis `local` e `test` (ver README).
-- Todos os dados abaixo sao FICTICIOS. O repositorio e publico: nao coloque dados reais aqui.
-- Roda depois do seed de tenant e usuarios (R__10), de onde vem o tenant `gaspar-sc`.
-- Migration repetivel e idempotente (nao duplica nem sobrescreve registros ja existentes).
--
-- Beta: um unico produto pre-cadastrado por tenant.

INSERT INTO produto (id, tenant_id, nome)
SELECT '00000000-0000-4000-8000-000000000501', 'gaspar-sc', 'Larvicida (exemplo)'
WHERE NOT EXISTS (SELECT 1 FROM produto WHERE id = '00000000-0000-4000-8000-000000000501');
