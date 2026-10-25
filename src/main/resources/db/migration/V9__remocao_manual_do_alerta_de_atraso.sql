-- Remocao manual do alerta de atraso (GEOPRAG-216). O instante da remocao fica na propria data agendada (a recorrencia
-- atrasada), entao a proxima recorrencia que atrasar nasce sem remocao e o alerta volta sozinho. Nulo = nao removido.
-- SQL portavel entre PostgreSQL e H2 (modo PostgreSQL).

ALTER TABLE data_agendada ADD COLUMN alerta_removido_em TIMESTAMP WITH TIME ZONE;

-- Nova acao na trilha de auditoria de pontos.
ALTER TABLE ponto_auditoria DROP CONSTRAINT ck_ponto_auditoria_acao;
ALTER TABLE ponto_auditoria ADD CONSTRAINT ck_ponto_auditoria_acao CHECK (acao IN ('CRIACAO', 'EDICAO',
    'ATRIBUICAO_APLICADOR', 'REMOCAO_APLICADOR', 'ATIVACAO', 'DESATIVACAO', 'REATIVACAO', 'CANCELAMENTO',
    'REMOCAO_ALERTA_ATRASO'));
