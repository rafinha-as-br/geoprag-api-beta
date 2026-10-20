-- Identidade da API: tenants (municipios) e usuarios (administrador, sub-administrador e aplicador).
-- SQL portavel entre PostgreSQL e H2 (modo PostgreSQL), usado nos testes.

CREATE TABLE tenant (
    -- Identificador textual estavel (slug), o mesmo `tenantId` do contrato v0 (ex.: gaspar-sc).
    id          VARCHAR(60)  NOT NULL,
    nome        VARCHAR(150) NOT NULL,
    criado_em   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tenant PRIMARY KEY (id)
);

CREATE TABLE usuario (
    -- Gerado pelo servidor (UUID); no contrato v0 o `id` do usuario e uma string opaca.
    id          UUID         NOT NULL,
    tenant_id   VARCHAR(60)  NOT NULL,
    nome        VARCHAR(150) NOT NULL,
    -- Sempre em minusculas: a unicidade e a busca por e-mail nao dependem de caixa.
    email       VARCHAR(254) NOT NULL,
    -- Somente os 11 digitos, sem mascara. Nunca em logs nem em respostas da API.
    cpf         VARCHAR(11)  NOT NULL,
    perfil      VARCHAR(20)  NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    -- Hash BCrypt da senha; a senha em texto nunca e guardada.
    senha_hash  VARCHAR(100) NOT NULL,
    criado_em   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT fk_usuario_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    -- O login (CPF ou e-mail) nao informa o tenant, entao cada identificador precisa ser unico
    -- globalmente; afrouxar para (tenant_id, ...) e possivel depois, apertar nao.
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT uk_usuario_cpf UNIQUE (cpf),
    CONSTRAINT ck_usuario_email_minusculo CHECK (email = LOWER(email)),
    CONSTRAINT ck_usuario_cpf_tamanho CHECK (CHAR_LENGTH(cpf) = 11),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMINISTRADOR', 'SUB_ADMINISTRADOR', 'APLICADOR')),
    CONSTRAINT ck_usuario_status CHECK (status IN ('ATIVO', 'DESATIVADO'))
);

CREATE INDEX ix_usuario_tenant_perfil ON usuario (tenant_id, perfil);
