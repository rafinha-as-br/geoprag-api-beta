-- Nucleo da Gestao de Aplicacoes: bairros, pontos de aplicacao, agendamento (recorrencia) e datas agendadas.
-- SQL portavel entre PostgreSQL e H2 (modo PostgreSQL), usado nos testes.
-- Toda tabela de negocio tem `tenant_id`. As chaves estrangeiras entre elas sao compostas (id, tenant_id), entao o
-- banco recusa um ponto com aplicador ou bairro de outro tenant, e uma data agendada de ponto de outro tenant.

-- Alvo das chaves estrangeiras compostas (ponto -> aplicador).
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE bairro (
    -- Gerado pelo servidor (UUID); no contrato v0 o `id` do bairro e uma string opaca.
    id          UUID         NOT NULL,
    tenant_id   VARCHAR(60)  NOT NULL,
    nome        VARCHAR(100) NOT NULL,
    criado_em   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_bairro PRIMARY KEY (id),
    CONSTRAINT fk_bairro_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT uk_bairro_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uk_bairro_tenant_nome UNIQUE (tenant_id, nome)
);

CREATE TABLE ponto (
    -- Gerado pelo servidor (UUID); no contrato v0 o `id` do ponto e uma string opaca.
    id                                UUID          NOT NULL,
    tenant_id                         VARCHAR(60)   NOT NULL,
    -- Codigo de exibicao (ex.: PA-001), unico dentro do tenant.
    identificador                     VARCHAR(20)   NOT NULL,
    nome                              VARCHAR(150)  NOT NULL,
    bairro_id                         UUID          NOT NULL,
    endereco                          VARCHAR(200)  NOT NULL,
    numero_referencia                 VARCHAR(30),
    descricao_do_trecho               VARCHAR(500),
    -- Medidas do trecho: opcionais no cadastro (a vazao e derivada e nao e guardada).
    largura_metros                    NUMERIC(8, 2),
    profundidade_metros               NUMERIC(8, 2),
    velocidade_metros_por_segundo     NUMERIC(8, 2),
    dosagem_ml                        NUMERIC(8, 2) NOT NULL,
    distancia_entre_subpontos_metros  NUMERIC(8, 2) NOT NULL,
    quantidade_de_subpontos           INTEGER       NOT NULL,
    -- Dias corridos de tolerancia de atraso; 4 quando o cadastro nao informa.
    tolerancia_atraso_dias            INTEGER       NOT NULL DEFAULT 4,
    -- Aplicador atribuido (usuario de perfil APLICADOR, validado pelo servico).
    aplicador_id                      UUID,
    -- O servidor e a autoridade sobre o estado.
    estado                            VARCHAR(20)   NOT NULL,
    -- Estado antes da ultima transicao (usado para reativar um ponto desativado).
    estado_anterior                   VARCHAR(20),
    -- O cadastro NAO tem coordenadas: ficam nulas e so a primeira aplicacao em campo as define.
    latitude                          NUMERIC(9, 6),
    longitude                         NUMERIC(9, 6),
    criado_em                         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ponto PRIMARY KEY (id),
    CONSTRAINT fk_ponto_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_ponto_bairro FOREIGN KEY (bairro_id, tenant_id) REFERENCES bairro (id, tenant_id),
    CONSTRAINT fk_ponto_aplicador FOREIGN KEY (aplicador_id, tenant_id) REFERENCES usuario (id, tenant_id),
    CONSTRAINT uk_ponto_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uk_ponto_tenant_identificador UNIQUE (tenant_id, identificador),
    CONSTRAINT ck_ponto_estado CHECK (estado IN ('ENDERECADA', 'DIRECIONADA', 'ATIVA', 'INATIVA', 'DESATIVADO')),
    CONSTRAINT ck_ponto_estado_anterior CHECK (estado_anterior IS NULL
        OR estado_anterior IN ('ENDERECADA', 'DIRECIONADA', 'ATIVA', 'INATIVA', 'DESATIVADO')),
    -- Sem aplicador o ponto so pode estar enderecado (ou inativo/desativado); direcionado e ativo exigem aplicador.
    CONSTRAINT ck_ponto_enderecada_sem_aplicador CHECK (estado <> 'ENDERECADA' OR aplicador_id IS NULL),
    CONSTRAINT ck_ponto_direcionada_ativa_com_aplicador CHECK (
        estado NOT IN ('DIRECIONADA', 'ATIVA') OR aplicador_id IS NOT NULL),
    CONSTRAINT ck_ponto_dosagem CHECK (dosagem_ml > 0),
    CONSTRAINT ck_ponto_distancia CHECK (distancia_entre_subpontos_metros > 0),
    CONSTRAINT ck_ponto_quantidade CHECK (quantidade_de_subpontos > 0),
    CONSTRAINT ck_ponto_tolerancia CHECK (tolerancia_atraso_dias >= 0),
    CONSTRAINT ck_ponto_largura CHECK (largura_metros IS NULL OR largura_metros > 0),
    CONSTRAINT ck_ponto_profundidade CHECK (profundidade_metros IS NULL OR profundidade_metros > 0),
    CONSTRAINT ck_ponto_velocidade CHECK (velocidade_metros_por_segundo IS NULL OR velocidade_metros_por_segundo > 0),
    -- Ou as duas coordenadas, validas, ou nenhuma (NULL em CHECK conta como aprovado, entao o NOT NULL e explicito).
    CONSTRAINT ck_ponto_coordenadas CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL
            AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))
);

CREATE INDEX ix_ponto_tenant_estado ON ponto (tenant_id, estado);
CREATE INDEX ix_ponto_tenant_bairro ON ponto (tenant_id, bairro_id);
CREATE INDEX ix_ponto_tenant_aplicador ON ponto (tenant_id, aplicador_id);

CREATE TABLE agendamento (
    id                      UUID        NOT NULL,
    tenant_id               VARCHAR(60) NOT NULL,
    ponto_id                UUID        NOT NULL,
    data_inicio             DATE        NOT NULL,
    intervalo_dias          INTEGER     NOT NULL,
    quantidade_recorrencias INTEGER     NOT NULL,
    -- Um ponto reativado ganha um agendamento novo e o anterior fica como historico (vigente = FALSE).
    -- Existir no maximo um vigente por ponto e garantido pelo servico.
    vigente                 BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_agendamento PRIMARY KEY (id),
    CONSTRAINT fk_agendamento_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_agendamento_ponto FOREIGN KEY (ponto_id, tenant_id) REFERENCES ponto (id, tenant_id),
    CONSTRAINT uk_agendamento_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_agendamento_intervalo CHECK (intervalo_dias > 0),
    CONSTRAINT ck_agendamento_recorrencias CHECK (quantidade_recorrencias > 0)
);

CREATE INDEX ix_agendamento_tenant_ponto ON agendamento (tenant_id, ponto_id);

CREATE TABLE data_agendada (
    -- Gerado pelo servidor (UUID); referenciado pelo registro de aplicacao (`dataAgendadaId`).
    id              UUID        NOT NULL,
    tenant_id       VARCHAR(60) NOT NULL,
    agendamento_id  UUID        NOT NULL,
    -- Data de calendario, no fuso do tenant.
    data_prevista   DATE        NOT NULL,
    status          VARCHAR(20) NOT NULL,
    criado_em       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_data_agendada PRIMARY KEY (id),
    CONSTRAINT fk_data_agendada_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_data_agendada_agendamento FOREIGN KEY (agendamento_id, tenant_id)
        REFERENCES agendamento (id, tenant_id),
    CONSTRAINT uk_data_agendada_agendamento_data UNIQUE (agendamento_id, data_prevista),
    CONSTRAINT ck_data_agendada_status CHECK (status IN ('PENDENTE', 'CONCLUIDA', 'CANCELADA'))
);

CREATE INDEX ix_data_agendada_tenant_status_data ON data_agendada (tenant_id, status, data_prevista);
