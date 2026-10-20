-- Produto minimo do beta: um unico produto pre-cadastrado por tenant, selecionado pelo aplicador ao registrar uma
-- aplicacao. Sem CRUD, sem lote, sem estoque e sem foto/anexo.
-- SQL portavel entre PostgreSQL e H2 (modo PostgreSQL), usado nos testes.

CREATE TABLE produto (
    -- Gerado pelo servidor (UUID); e o `produtoId` do registro de aplicacao no contrato v0 (string opaca).
    id          UUID         NOT NULL,
    tenant_id   VARCHAR(60)  NOT NULL,
    nome        VARCHAR(150) NOT NULL,
    criado_em   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_produto PRIMARY KEY (id),
    CONSTRAINT fk_produto_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    -- Alvo das chaves estrangeiras compostas das tabelas que referenciam o produto (registro de aplicacao).
    CONSTRAINT uk_produto_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uk_produto_tenant_nome UNIQUE (tenant_id, nome),
    CONSTRAINT ck_produto_nome_preenchido CHECK (CHAR_LENGTH(TRIM(nome)) > 0)
);
