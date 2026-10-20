-- SEED DE DESENVOLVIMENTO: carregado SOMENTE nos perfis `local` e `test` (ver README).
-- Todos os dados abaixo sao FICTICIOS. O repositorio e publico: nao coloque dados reais aqui.
-- Roda depois do seed de tenant e usuarios (R__10), cujos aplicadores sao usados aqui
-- (ids 00000000-0000-4000-8000-00000000000{3,4}).
-- Migration repetivel e idempotente (nao duplica nem sobrescreve registros ja existentes).
-- Datas fixas (nao relativas a data de execucao): PA-003 tem datas pendentes antigas, entao esta em ATRASO.
--
-- Bairros: Centro, Bela Vista e Margem do Rio.
-- Pontos (um por estado, mais um ativo no prazo):
--   PA-001 ENDERECADA | PA-002 DIRECIONADA | PA-003 ATIVA (com data em atraso) | PA-004 ATIVA (no prazo)
--   PA-005 INATIVA    | PA-006 DESATIVADO

INSERT INTO bairro (id, tenant_id, nome)
SELECT '00000000-0000-4000-8000-000000000101', 'gaspar-sc', 'Centro'
WHERE NOT EXISTS (SELECT 1 FROM bairro WHERE id = '00000000-0000-4000-8000-000000000101');

INSERT INTO bairro (id, tenant_id, nome)
SELECT '00000000-0000-4000-8000-000000000102', 'gaspar-sc', 'Bela Vista'
WHERE NOT EXISTS (SELECT 1 FROM bairro WHERE id = '00000000-0000-4000-8000-000000000102');

INSERT INTO bairro (id, tenant_id, nome)
SELECT '00000000-0000-4000-8000-000000000103', 'gaspar-sc', 'Margem do Rio'
WHERE NOT EXISTS (SELECT 1 FROM bairro WHERE id = '00000000-0000-4000-8000-000000000103');

-- PA-001: enderecado, sem aplicador, sem agendamento.
INSERT INTO ponto (id, tenant_id, identificador, nome, bairro_id, endereco, numero_referencia, descricao_do_trecho,
                   dosagem_ml, distancia_entre_subpontos_metros, quantidade_de_subpontos, aplicador_id, estado)
SELECT '00000000-0000-4000-8000-000000000201', 'gaspar-sc', 'PA-001', 'Rua das Flores, trecho 1',
       '00000000-0000-4000-8000-000000000101', 'Rua das Flores', '120', 'Da esquina com a Rua A ate a Rua B',
       20.00, 10.00, 10, NULL, 'ENDERECADA'
WHERE NOT EXISTS (SELECT 1 FROM ponto WHERE id = '00000000-0000-4000-8000-000000000201');

-- PA-002: direcionado ao aplicador 3, ainda sem agendamento.
INSERT INTO ponto (id, tenant_id, identificador, nome, bairro_id, endereco, descricao_do_trecho,
                   largura_metros, profundidade_metros, velocidade_metros_por_segundo,
                   dosagem_ml, distancia_entre_subpontos_metros, quantidade_de_subpontos, aplicador_id, estado)
SELECT '00000000-0000-4000-8000-000000000202', 'gaspar-sc', 'PA-002', 'Rua das Palmeiras, trecho 2',
       '00000000-0000-4000-8000-000000000101', 'Rua das Palmeiras', 'Trecho ao longo da canaleta',
       1.50, 0.30, 0.80, 20.00, 10.00, 8, '00000000-0000-4000-8000-000000000003', 'DIRECIONADA'
WHERE NOT EXISTS (SELECT 1 FROM ponto WHERE id = '00000000-0000-4000-8000-000000000202');

-- PA-003: ativo com tolerancia padrao (4 dias) e DATAS EM ATRASO (2026-10-12 e 2026-10-19 pendentes).
INSERT INTO ponto (id, tenant_id, identificador, nome, bairro_id, endereco, numero_referencia,
                   dosagem_ml, distancia_entre_subpontos_metros, quantidade_de_subpontos, aplicador_id, estado,
                   estado_anterior)
SELECT '00000000-0000-4000-8000-000000000203', 'gaspar-sc', 'PA-003', 'Travessa da Colina',
       '00000000-0000-4000-8000-000000000102', 'Travessa da Colina', '45',
       25.00, 5.00, 6, '00000000-0000-4000-8000-000000000003', 'ATIVA', 'DIRECIONADA'
WHERE NOT EXISTS (SELECT 1 FROM ponto WHERE id = '00000000-0000-4000-8000-000000000203');

INSERT INTO agendamento (id, tenant_id, ponto_id, data_inicio, intervalo_dias, quantidade_recorrencias, vigente)
SELECT '00000000-0000-4000-8000-000000000301', 'gaspar-sc', '00000000-0000-4000-8000-000000000203',
       DATE '2026-10-05', 7, 3, TRUE
WHERE NOT EXISTS (SELECT 1 FROM agendamento WHERE id = '00000000-0000-4000-8000-000000000301');

INSERT INTO data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
SELECT '00000000-0000-4000-8000-000000000401', 'gaspar-sc', '00000000-0000-4000-8000-000000000301',
       DATE '2026-10-05', 'CONCLUIDA'
WHERE NOT EXISTS (SELECT 1 FROM data_agendada WHERE id = '00000000-0000-4000-8000-000000000401');

INSERT INTO data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
SELECT '00000000-0000-4000-8000-000000000402', 'gaspar-sc', '00000000-0000-4000-8000-000000000301',
       DATE '2026-10-12', 'PENDENTE'
WHERE NOT EXISTS (SELECT 1 FROM data_agendada WHERE id = '00000000-0000-4000-8000-000000000402');

INSERT INTO data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
SELECT '00000000-0000-4000-8000-000000000403', 'gaspar-sc', '00000000-0000-4000-8000-000000000301',
       DATE '2026-10-19', 'PENDENTE'
WHERE NOT EXISTS (SELECT 1 FROM data_agendada WHERE id = '00000000-0000-4000-8000-000000000403');

-- PA-004: ativo no prazo (datas futuras), aplicador 4, tolerancia de 2 dias.
INSERT INTO ponto (id, tenant_id, identificador, nome, bairro_id, endereco,
                   dosagem_ml, distancia_entre_subpontos_metros, quantidade_de_subpontos, tolerancia_atraso_dias,
                   aplicador_id, estado, estado_anterior)
SELECT '00000000-0000-4000-8000-000000000204', 'gaspar-sc', 'PA-004', 'Alameda do Rio, trecho 1',
       '00000000-0000-4000-8000-000000000103', 'Alameda do Rio',
       30.00, 12.50, 12, 2, '00000000-0000-4000-8000-000000000004', 'ATIVA', 'DIRECIONADA'
WHERE NOT EXISTS (SELECT 1 FROM ponto WHERE id = '00000000-0000-4000-8000-000000000204');

INSERT INTO agendamento (id, tenant_id, ponto_id, data_inicio, intervalo_dias, quantidade_recorrencias, vigente)
SELECT '00000000-0000-4000-8000-000000000302', 'gaspar-sc', '00000000-0000-4000-8000-000000000204',
       DATE '2027-01-10', 14, 3, TRUE
WHERE NOT EXISTS (SELECT 1 FROM agendamento WHERE id = '00000000-0000-4000-8000-000000000302');

INSERT INTO data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
SELECT '00000000-0000-4000-8000-000000000404', 'gaspar-sc', '00000000-0000-4000-8000-000000000302',
       DATE '2027-01-10', 'PENDENTE'
WHERE NOT EXISTS (SELECT 1 FROM data_agendada WHERE id = '00000000-0000-4000-8000-000000000404');

INSERT INTO data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
SELECT '00000000-0000-4000-8000-000000000405', 'gaspar-sc', '00000000-0000-4000-8000-000000000302',
       DATE '2027-01-24', 'PENDENTE'
WHERE NOT EXISTS (SELECT 1 FROM data_agendada WHERE id = '00000000-0000-4000-8000-000000000405');

INSERT INTO data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
SELECT '00000000-0000-4000-8000-000000000406', 'gaspar-sc', '00000000-0000-4000-8000-000000000302',
       DATE '2027-02-07', 'PENDENTE'
WHERE NOT EXISTS (SELECT 1 FROM data_agendada WHERE id = '00000000-0000-4000-8000-000000000406');

-- PA-005: inativo (aplicacao cancelada): historico preservado, mantem o aplicador 4.
INSERT INTO ponto (id, tenant_id, identificador, nome, bairro_id, endereco,
                   dosagem_ml, distancia_entre_subpontos_metros, quantidade_de_subpontos,
                   aplicador_id, estado, estado_anterior)
SELECT '00000000-0000-4000-8000-000000000205', 'gaspar-sc', 'PA-005', 'Rua do Porto, trecho 1',
       '00000000-0000-4000-8000-000000000103', 'Rua do Porto',
       20.00, 10.00, 5, '00000000-0000-4000-8000-000000000004', 'INATIVA', 'ATIVA'
WHERE NOT EXISTS (SELECT 1 FROM ponto WHERE id = '00000000-0000-4000-8000-000000000205');

INSERT INTO agendamento (id, tenant_id, ponto_id, data_inicio, intervalo_dias, quantidade_recorrencias, vigente)
SELECT '00000000-0000-4000-8000-000000000303', 'gaspar-sc', '00000000-0000-4000-8000-000000000205',
       DATE '2026-09-01', 7, 2, FALSE
WHERE NOT EXISTS (SELECT 1 FROM agendamento WHERE id = '00000000-0000-4000-8000-000000000303');

INSERT INTO data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
SELECT '00000000-0000-4000-8000-000000000407', 'gaspar-sc', '00000000-0000-4000-8000-000000000303',
       DATE '2026-09-01', 'CONCLUIDA'
WHERE NOT EXISTS (SELECT 1 FROM data_agendada WHERE id = '00000000-0000-4000-8000-000000000407');

INSERT INTO data_agendada (id, tenant_id, agendamento_id, data_prevista, status)
SELECT '00000000-0000-4000-8000-000000000408', 'gaspar-sc', '00000000-0000-4000-8000-000000000303',
       DATE '2026-09-08', 'CANCELADA'
WHERE NOT EXISTS (SELECT 1 FROM data_agendada WHERE id = '00000000-0000-4000-8000-000000000408');

-- PA-006: desativado (estava direcionado ao aplicador 3), sem agendamento.
INSERT INTO ponto (id, tenant_id, identificador, nome, bairro_id, endereco,
                   dosagem_ml, distancia_entre_subpontos_metros, quantidade_de_subpontos,
                   aplicador_id, estado, estado_anterior)
SELECT '00000000-0000-4000-8000-000000000206', 'gaspar-sc', 'PA-006', 'Rua Antiga, trecho 3',
       '00000000-0000-4000-8000-000000000102', 'Rua Antiga',
       15.00, 8.00, 4, '00000000-0000-4000-8000-000000000003', 'DESATIVADO', 'DIRECIONADA'
WHERE NOT EXISTS (SELECT 1 FROM ponto WHERE id = '00000000-0000-4000-8000-000000000206');
