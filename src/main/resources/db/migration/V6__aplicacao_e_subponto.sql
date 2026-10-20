-- Registro de aplicacao feito em campo: `aplicacao` agrupa os subpontos da mesma aplicacao e `subponto` e cada
-- item enviado pelo app. SQL portavel entre PostgreSQL e H2 (modo PostgreSQL), usado nos testes.
--
-- O `id` de ambas e o UUID gerado no app: a chave primaria torna o reenvio idempotente.
-- Os dados de campo sao APPEND-ONLY: nunca se editam nem se apagam (o servico e as entidades nao tem como atualizar
-- ou excluir um subponto; so a `aplicacao` muda de status ao concluir).
-- Conflito = o servidor ACEITA e MARCA o registro com um motivo; o trabalho feito em campo nunca e descartado.

-- Alvo da chave estrangeira composta aplicacao -> data_agendada (a V3 so criou essa chave unica nas outras tabelas).
ALTER TABLE data_agendada ADD CONSTRAINT uk_data_agendada_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE aplicacao (
    id               UUID        NOT NULL,
    tenant_id        VARCHAR(60) NOT NULL,
    ponto_id         UUID        NOT NULL,
    data_agendada_id UUID        NOT NULL,
    produto_id       UUID        NOT NULL,
    -- Aplicador que fez o registro: vem do token, nunca do corpo do pedido.
    aplicador_id     UUID        NOT NULL,
    -- Concluida pelo sistema quando atinge a quantidade de subpontos do ponto.
    status           VARCHAR(20) NOT NULL DEFAULT 'EM_ANDAMENTO',
    concluida_em     TIMESTAMP WITH TIME ZONE,
    criado_em        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_aplicacao PRIMARY KEY (id),
    CONSTRAINT fk_aplicacao_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_aplicacao_ponto FOREIGN KEY (ponto_id, tenant_id) REFERENCES ponto (id, tenant_id),
    CONSTRAINT fk_aplicacao_data_agendada FOREIGN KEY (data_agendada_id, tenant_id)
        REFERENCES data_agendada (id, tenant_id),
    CONSTRAINT fk_aplicacao_produto FOREIGN KEY (produto_id, tenant_id) REFERENCES produto (id, tenant_id),
    CONSTRAINT fk_aplicacao_aplicador FOREIGN KEY (aplicador_id, tenant_id) REFERENCES usuario (id, tenant_id),
    CONSTRAINT uk_aplicacao_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_aplicacao_status CHECK (status IN ('EM_ANDAMENTO', 'CONCLUIDA')),
    CONSTRAINT ck_aplicacao_concluida_em CHECK ((status = 'CONCLUIDA') = (concluida_em IS NOT NULL))
);

CREATE INDEX ix_aplicacao_tenant_ponto ON aplicacao (tenant_id, ponto_id);
CREATE INDEX ix_aplicacao_tenant_data ON aplicacao (tenant_id, data_agendada_id);

CREATE TABLE subponto (
    id             UUID          NOT NULL,
    tenant_id      VARCHAR(60)   NOT NULL,
    aplicacao_id   UUID          NOT NULL,
    latitude       NUMERIC(9, 6) NOT NULL,
    longitude      NUMERIC(9, 6) NOT NULL,
    -- Relogio do aparelho, com o offset de origem.
    realizado_em   TIMESTAMP WITH TIME ZONE NOT NULL,
    -- Relogio do SERVIDOR, definido na gravacao; nunca vem do cliente.
    recebido_em    TIMESTAMP WITH TIME ZONE NOT NULL,
    conflito       BOOLEAN       NOT NULL DEFAULT FALSE,
    motivo_conflito VARCHAR(30),
    CONSTRAINT pk_subponto PRIMARY KEY (id),
    CONSTRAINT fk_subponto_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_subponto_aplicacao FOREIGN KEY (aplicacao_id, tenant_id) REFERENCES aplicacao (id, tenant_id),
    CONSTRAINT ck_subponto_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_subponto_longitude CHECK (longitude BETWEEN -180 AND 180),
    -- Com conflito o motivo e obrigatorio; sem conflito nao pode haver motivo.
    CONSTRAINT ck_subponto_conflito_motivo CHECK (
        (conflito = TRUE AND motivo_conflito IS NOT NULL) OR (conflito = FALSE AND motivo_conflito IS NULL)),
    CONSTRAINT ck_subponto_motivo CHECK (motivo_conflito IS NULL OR motivo_conflito IN (
        'PONTO_REATRIBUIDO', 'PONTO_DESATIVADO', 'PONTO_INATIVO', 'DATA_JA_ATENDIDA', 'DATA_CANCELADA'))
);

CREATE INDEX ix_subponto_tenant_aplicacao ON subponto (tenant_id, aplicacao_id);
CREATE INDEX ix_subponto_tenant_conflito ON subponto (tenant_id, conflito);
