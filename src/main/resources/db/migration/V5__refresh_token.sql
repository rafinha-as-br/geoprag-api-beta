-- Refresh token do aplicador: opaco, valido por 30 dias, guardado SOMENTE como hash (SHA-256 em hexadecimal).
-- Sem revogacao no beta: um token pode ser removido manualmente. A rotacao apaga o anterior ao emitir o novo.
-- SQL portavel entre PostgreSQL e H2 (modo PostgreSQL), usado nos testes.

CREATE TABLE refresh_token (
    id          UUID        NOT NULL,
    tenant_id   VARCHAR(60) NOT NULL,
    usuario_id  UUID        NOT NULL,
    -- SHA-256 do valor entregue ao app, em hexadecimal (64 caracteres). O valor em texto puro nunca e gravado.
    token_hash  VARCHAR(64) NOT NULL,
    expira_em   TIMESTAMP WITH TIME ZONE NOT NULL,
    criado_em   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT fk_refresh_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (usuario_id, tenant_id) REFERENCES usuario (id, tenant_id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_hash_tamanho CHECK (CHAR_LENGTH(token_hash) = 64)
);

CREATE INDEX ix_refresh_token_usuario ON refresh_token (usuario_id);
