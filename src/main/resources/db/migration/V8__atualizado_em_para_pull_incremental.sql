-- Pull incremental (GET /sync/pull?desde=...): o servidor precisa saber o que mudou depois de um instante. As entidades
-- mantem `atualizado_em` (na criacao e a cada alteracao); `subponto.recebido_em` ja cobre os registros de campo.
-- Linhas existentes recebem o instante da migration. SQL portavel entre PostgreSQL e H2 (modo PostgreSQL).

ALTER TABLE ponto ADD COLUMN atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE agendamento ADD COLUMN atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE data_agendada ADD COLUMN atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
