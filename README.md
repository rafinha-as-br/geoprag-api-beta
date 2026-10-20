# geoprag-api-beta

API de desenvolvimento (beta) do projeto GeoPrag: Spring Boot (Java 21) + PostgreSQL.

## Stack e decisões

| Item | Escolha |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA) |
| Build | **Maven** (com Maven Wrapper) |
| Banco | PostgreSQL 16 |
| Migrations | Flyway (`src/main/resources/db/migration`) |
| Execução local | Docker + Docker Compose |

## Subir o ambiente

Requisitos: Docker e Docker Compose.

```bash
docker compose up --build
```

Isso sobe o PostgreSQL e a API (porta **8080**). Na primeira inicialização o Flyway executa as migrations do esquema (`V1` vazia, `V2` com `tenant` e `usuario`, `V3` com `bairro`, `ponto`, `agendamento` e `data_agendada`, `V4` com `produto`, `V5` com `refresh_token` e `V6` com `aplicacao` e `subponto`) e, no perfil `local` (padrão do Compose), o seed fictício descrito abaixo.

Para customizar a configuração, copie o exemplo e ajuste (o `.env` é ignorado pelo Git):

```bash
cp .env.example .env
```

Sem `.env`, o Compose usa valores padrão de desenvolvimento local para o banco (que não é publicado fora da rede do Compose). **Esses padrões servem só para o ambiente local**: em qualquer ambiente compartilhado, defina `DB_PASSWORD` e `CORS_ALLOWED_ORIGINS` no `.env`. **`JWT_SECRET` é obrigatório em qualquer ambiente**: sem um segredo com pelo menos 32 caracteres a API não sobe (o Compose avisa antes de iniciar). Gere um com `openssl rand -base64 48` e coloque no `.env`.

## Configuração

Toda a configuração vem **somente de variáveis de ambiente** (veja `.env.example`):

| Variável | Uso |
|---|---|
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Banco de dados (no Compose, `DB_URL` é montada a partir delas) |
| `DB_URL` | URL JDBC. Ao rodar a API fora do Compose, `DB_URL`, `DB_USER` e `DB_PASSWORD` são obrigatórias |
| `JWT_SECRET`, `JWT_ACCESS_TOKEN_MINUTES` | Autenticação JWT (HS256). `JWT_SECRET` é obrigatório (32+ caracteres, senão a API não sobe); o access token vale 15 minutos por padrão |
| `APP_FUSO_HORARIO`, `PONTO_MAX_RECORRENCIAS` | Fuso das datas de calendário (padrão `America/Sao_Paulo`) e teto de datas geradas na ativação de um ponto (padrão 365) |
| `SYNC_MAX_ITENS_POR_LOTE` | Máximo de itens em `POST /sync/aplicacoes` (padrão 500; acima disso a API responde `413`) |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas (lista separada por vírgula) |
| `SPRING_PROFILES_ACTIVE` | Perfil do Spring. `local` (padrão do Compose) carrega o seed fictício; sem ele o seed não existe |

> O repositório é público: nunca versione `.env`, chaves ou credenciais reais.

## Pontos de aplicação (modelo de dados)

- **Esquema** (`V3`): `bairro`, `ponto`, `agendamento` (recorrência; um ponto reativado ganha um novo e o anterior fica como histórico) e `data_agendada` (`PENDENTE`, `CONCLUIDA` ou `CANCELADA`). Toda tabela tem `tenant_id`, e as chaves entre elas são compostas `(id, tenant_id)`: o banco recusa ponto com aplicador ou bairro de outro tenant.
- **Estados do ponto:** `ENDERECADA`, `DIRECIONADA`, `ATIVA`, `INATIVA` e `DESATIVADO`; o servidor é a autoridade. Sem aplicador o ponto só pode estar enderecado, inativo ou desativado; direcionado e ativo exigem aplicador (check no banco). Um ponto nunca é excluído, apenas desativado.
- **Cadastro sem coordenadas:** `latitude` e `longitude` ficam nulas; só a primeira aplicação em campo as define. A tolerância de atraso é por ponto (dias corridos, padrão 4).
- **Repositórios** (`ponto.repository`) estendem `Repository` (não `JpaRepository`): toda consulta recebe o `tenantId` do token e não há `delete`.
- **Seed** (`R__20`, mesmos perfis `local` e `test`): 3 bairros e 6 pontos, um por estado mais um segundo ativo (`PA-001` a `PA-006`). O `PA-003` tem datas pendentes antigas, ou seja, **em atraso** (as datas do seed são fixas, não relativas ao dia da execução).

### Endpoints de leitura (portal)

Só `ADMINISTRADOR` e `SUB_ADMINISTRADOR` (outro perfil: `403 ACESSO_NEGADO`); o tenant vem do token.

| Endpoint | O que devolve |
|---|---|
| `GET /api/v1/pontos` | Pontos do tenant em ordem de `identificador`, no modelo `Ponto` do contrato v0 (com `agendamento.datas[]` e `subpontos`). Filtros opcionais: `bairro` (nome, sem diferenciar maiúsculas) e `estado`. |
| `GET /api/v1/pontos/{id}` | Um ponto. Ponto inexistente, de outro tenant ou com id malformado: `404 PONTO_NAO_ENCONTRADO` (nunca 403). |
| `GET /api/v1/bairros` | Bairros do tenant (`id`, `nome`), em ordem alfabética. |
| `GET /api/v1/aplicadores` | Aplicadores do tenant (`id`, `nome`, `status`), em ordem alfabética; filtro opcional `status`. Nunca expõe CPF nem e-mail. |

O `agendamento` é o vigente; sem vigente (ex.: ponto inativo), o mais recente, que guarda o histórico. Os `subpontos` trazem também `conflito` e `motivoConflito` (campos opcionais além do contrato v0, visíveis só a esses perfis). Datas e horas dos registros saem em UTC com offset. Sem paginação (fora do beta).

### Transições de estado do ponto (portal)

Mesmos perfis e tenant da leitura; todas devolvem o `Ponto` atualizado. **O servidor é a autoridade sobre o estado**: operação que o estado não permite retorna `409 OPERACAO_NAO_PERMITIDA` com o motivo em pt-BR no `detail` e não altera nada. Ponto inexistente ou de outro tenant: `404`.

| Endpoint | Efeito |
|---|---|
| `POST /api/v1/pontos/{id}/aplicador` (`{ "aplicadorId" }`) | Atribui ou troca o aplicador. |
| `DELETE /api/v1/pontos/{id}/aplicador` | Remove o aplicador. |
| `POST /api/v1/pontos/{id}/ativacao` (`{ "dataInicio", "intervaloDias", "quantidadeRecorrencias" }`) | Ativa o ponto e gera as datas (`dataInicio + i × intervaloDias`). |

| Estado | Atribuir / trocar | Remover | Ativar |
|---|---|---|---|
| `ENDERECADA` | permitido (vai a `DIRECIONADA`) | 409 | 409 (sem aplicador) |
| `DIRECIONADA` | permitido (troca; continua `DIRECIONADA`) | permitido (volta a `ENDERECADA`) | permitido (vai a `ATIVA`) |
| `ATIVA` | 409 (cancele a aplicação antes) | 409 | 409 |
| `INATIVA` | permitido (troca; continua `INATIVA`) | 409 (mantém o aplicador) | permitido (vai a `ATIVA`) |
| `DESATIVADO` | 409 | 409 | 409 |

- `estadoAnterior` só muda quando o estado muda (trocar o aplicador não é transição). Repetir o mesmo aplicador é um no-op `200`.
- **Aplicador:** inexistente, de outro tenant ou que não tem o perfil `APLICADOR` → `400 VALIDACAO_INVALIDA` (campo `aplicadorId`, sem revelar outro tenant); aplicador `DESATIVADO` do próprio tenant → `409`.
- **Ativação:** `dataInicio` não pode ser anterior a hoje (no fuso `APP_FUSO_HORARIO`, padrão `America/Sao_Paulo`; ainda não há fuso por tenant); `intervaloDias` ≥ 1 sem máximo, desde que as datas não passem do ano 9999; `quantidadeRecorrencias` ≥ 1 e até `PONTO_MAX_RECORRENCIAS` (padrão 365). Em todos os casos inválidos: `400 VALIDACAO_INVALIDA` com `errors[]`.
- **Um único agendamento vigente por ponto**, garantido pelo serviço: ao reativar um ponto `INATIVA`, o agendamento anterior vira histórico (`vigente = false`) e as datas ainda pendentes dele passam a `CANCELADA`.
- **Concorrência:** a mutação lê o ponto com `SELECT … FOR UPDATE`; duas ativações simultâneas do mesmo ponto resultam em um `200` e um `409` (testado em PostgreSQL real).

### Cadastro e ciclo de vida do ponto (portal)

Mesmos perfis, tenant e regras de erro (`404`, `409 OPERACAO_NAO_PERMITIDA`) das transições acima; todos devolvem o `Ponto`.

| Endpoint | Efeito |
|---|---|
| `POST /api/v1/pontos` | Cadastra um ponto. Obrigatórios: `nome`, `bairroId`, `endereco`, `dosagemMl`, `distanciaEntreSubpontosMetros`, `quantidadeDeSubpontos`; opcionais: `numeroReferencia`, `descricaoDoTrecho`, `larguraMetros`, `profundidadeMetros`, `velocidadeMetrosPorSegundo`, `toleranciaAtrasoDias` (padrão 4) e `aplicadorId`. Nasce `ENDERECADA`, ou `DIRECIONADA` com `aplicadorId`. O `identificador` (`PA-001`, `PA-002`, …) é gerado pelo servidor. |
| `PUT /api/v1/pontos/{id}` | Edita o cadastro: o corpo substitui (opcional omitido fica sem valor; tolerância omitida vale 4); `aplicadorId` é ignorado (o aplicador muda por `/aplicador`). `nome` e `toleranciaAtrasoDias` sempre; qualquer outro campo só enquanto o ponto **não tem agendamento vigente nem aplicação registrada**, senão `409`. |
| `POST /api/v1/pontos/{id}/desativacao` | Qualquer estado menos `DESATIVADO`: guarda o estado em `estadoAnterior` e cancela as datas **pendentes de hoje em diante** do agendamento vigente (o histórico é preservado). |
| `POST /api/v1/pontos/{id}/reativacao` | Só `DESATIVADO`: volta ao `estadoAnterior`. As datas canceladas na desativação não são restauradas. |
| `POST /api/v1/pontos/{id}/cancelamento` | Só `ATIVA` → `INATIVA`: encerra o agendamento vigente (`vigente = false`) e cancela as datas pendentes de hoje em diante; execuções e datas passadas ficam como estão. |

- `bairroId` ou `aplicadorId` inválidos ou de outro tenant: `400 VALIDACAO_INVALIDA` (campo indicado em `errors[]`); aplicador `DESATIVADO`: `409`. Medidas seguem `NUMERIC(8, 2)` (até 2 casas decimais); textos têm os limites do esquema.
- A criação serializa-se por `SELECT … FOR UPDATE` na linha do tenant: o `identificador` é a quantidade de pontos do tenant + 1, e pontos nunca são excluídos.

## Produto (beta)

- **Esquema** (`V4`): `produto` (`id` gerado pelo servidor, `nome`, `tenant_id`). No beta há **um único produto pré-cadastrado por tenant**, que o aplicador seleciona ao registrar uma aplicação; não há CRUD, lote, estoque nem foto. O `id` é o `produtoId` do contrato v0.
- **Seed** (`R__30`, mesmos perfis `local` e `test`, depois do seed de tenant e usuários): o produto fictício `Larvicida (exemplo)` no tenant `gaspar-sc`.
- O repositório (`produto.repository`) estende `Repository` (não `JpaRepository`): toda consulta recebe o `tenantId` do token. A tabela tem `UNIQUE (id, tenant_id)` para servir de alvo das chaves compostas do registro de aplicação.

## Registro de aplicação (serviço)

O registro de um item (subponto) enviado pelo app é feito pelo `RegistroAplicacaoService`; o endpoint `POST /api/v1/sync/aplicacoes` (abaixo) o chama por item, e `GET /sync/pull` é da GEOPRAG-200. Esquema em `V6`: `aplicacao` (agrupa os subpontos, `EM_ANDAMENTO` ou `CONCLUIDA`) e `subponto` (um por item), ambos com `tenant_id` e chaves compostas.

- **Idempotente:** o `id` do subponto é o UUID gerado no app e é a chave primária; reenviar o mesmo `id` devolve `DUPLICADO` e não altera nada.
- **Append-only:** subpontos nunca são editados nem apagados (sem setters, colunas não atualizáveis e repositório sem `delete`).
- **Datas:** `realizado_em` é o relógio do aparelho (com offset); `recebido_em` é sempre do servidor. O aplicador e o tenant vêm do token.
- **Rejeita só o inválido de fato** (`DADOS_INVALIDOS`, `PONTO_INEXISTENTE`, `PRODUTO_INEXISTENTE`), sem gravar nada. Dados inconsistentes incluem data agendada de outro ponto e `aplicacaoId` reutilizado com outro ponto, data, produto ou aplicador.
- **Conflito é aceito e marcado** com um motivo (`PONTO_REATRIBUIDO`, `PONTO_DESATIVADO`, `PONTO_INATIVO`, `DATA_JA_ATENDIDA`, `DATA_CANCELADA`; vale o primeiro da lista quando há mais de um). O resultado ao app não revela o conflito; a marca fica consultável por ponto (`SubpontoRepository`), para o gestor.
- **Conclusão:** ao atingir a quantidade de subpontos do ponto, a aplicação é concluída e a data agendada recebe baixa (se nenhuma outra aplicação disputa a mesma data).
- **`aplicacaoId` já usado em outro tenant** é rejeitado como `DADOS_INVALIDOS` (a chave primária da aplicação é global; a checagem não revela nada do outro tenant).

### Envio em lote: `POST /api/v1/sync/aplicacoes`

Só o perfil `APLICADOR`. O corpo é `{ "dispositivoId": "...", "itens": [ ... ] }` (cada item como no contrato v0: `id`, `aplicacaoId`, `pontoId`, `dataAgendadaId`, `produtoId`, `latitude`, `longitude`, `realizadoEm`). A resposta é `{ "recebidoEm": "...", "resultados": [ { "id", "status", "codigo?", "motivo?" } ] }`, **um resultado por item, na mesma ordem do envio**, com `status` `CRIADO`, `DUPLICADO` ou `REJEITADO`. O registro online usa o mesmo endpoint com um item.

- **Cada item é uma transação própria:** um item rejeitado não derruba os demais, e reenviar o lote inteiro devolve `DUPLICADO` para o que já foi gravado. Uma falha inesperada (ex.: banco fora do ar) **não** vira resultado de item: a resposta é `500` e o que já foi gravado antes permanece; o app reenvia o lote.
- **Envelope inválido** (`itens` ausente ou vazio, `dispositivoId` em branco, JSON ilegível) → `400`. Item nulo ou incompleto dentro de um lote válido é rejeitado como `DADOS_INVALIDOS`.
- **Limite:** mais de `SYNC_MAX_ITENS_POR_LOTE` itens (padrão **500**) → `413` com o código `LOTE_MUITO_GRANDE`, sem gravar nada.
- O aplicador e o tenant vêm do token; o corpo não os informa. A resposta nunca revela conflito.

## Usuários, senhas e seed

- **Esquema** (`db/migration`, `V2`): `tenant` (id textual, ex.: `gaspar-sc`) e `usuario` (perfil `ADMINISTRADOR`, `SUB_ADMINISTRADOR` ou `APLICADOR`; status `ATIVO` ou `DESATIVADO`; `nome`, `e-mail`, `cpf`, hash de senha e `tenant_id`). O SQL é portável entre PostgreSQL e H2.
- **Senha:** só o hash BCrypt é guardado.
- **CPF:** texto com somente os 11 dígitos, nunca em logs nem em respostas. E-mail e CPF são **únicos globalmente**, porque o login (CPF ou e-mail) não informa o tenant. O e-mail é guardado em minúsculas.
- **Seed fictício** (`db/seed`, migration repetível e idempotente): tenant `gaspar-sc` e contas de teste com CPFs sabidamente inválidos e e-mails `@example.com`. **Só é carregado nos perfis `local` e `test`** (`application-local.yml` e `application-test.yml` acrescentam `classpath:db/seed` à localização do Flyway); a configuração padrão e qualquer outro ambiente ficam sem ele. Para o Compose subir sem seed, defina `SPRING_PROFILES_ACTIVE=default` no `.env`.

| Conta | E-mail | Perfil | Status |
|---|---|---|---|
| Administrador Exemplo | `admin@example.com` | `ADMINISTRADOR` | `ATIVO` |
| Sub Administrador Exemplo | `subadmin@example.com` | `SUB_ADMINISTRADOR` | `ATIVO` |
| Aplicador Exemplo Um | `aplicador1@example.com` | `APLICADOR` | `ATIVO` |
| Aplicador Exemplo Dois | `aplicador2@example.com` | `APLICADOR` | `ATIVO` |
| Aplicador Exemplo Desativado | `aplicador.desativado@example.com` | `APLICADOR` | `DESATIVADO` |

A senha das contas de seed é `Geoprag@Local2026` (fictícia, **somente para uso local**; nunca a reutilize). Os CPFs de seed são sequências de dígitos repetidos (`111.111.111-11` a `555.555.555-55`).

## Autenticação

- `POST /api/v1/auth/login` (público): corpo `{ "identificador": "<CPF ou e-mail>", "senha": "..." }`; devolve `accessToken` (JWT HS256, 15 minutos), `expiresInSeconds` e `usuario`. O CPF pode ter máscara. Credencial incorreta (inclusive identificador inexistente) retorna `401 CREDENCIAIS_INVALIDAS`; usuário desativado, `403 USUARIO_DESATIVADO`.
- **Refresh token (só o `APLICADOR`)**: o login do aplicador devolve também `refreshToken`, opaco e válido por **30 dias** (administrador e sub-administrador não recebem). `POST /api/v1/auth/refresh` (público), corpo `{ "refreshToken": "..." }`, devolve um novo par `{ accessToken, refreshToken }` e **invalida o anterior** (rotação). Inválido, expirado, removido, de usuário desativado ou de outro perfil: `401 REFRESH_INVALIDO`; campo ausente: `400 VALIDACAO_INVALIDA`. No banco (`V5`, tabela `refresh_token`) fica só o hash SHA-256; o valor puro nunca é gravado nem logado. Não há revogação no beta: para derrubar uma sessão, apague a linha de `refresh_token`.
- `GET /api/v1/auth/me`: o usuário do token, para qualquer perfil.
- O token vai no header `Authorization: Bearer <accessToken>`. O `sub` é o id do usuário e o perfil (`ADMINISTRADOR`, `SUB_ADMINISTRADOR` ou `APLICADOR`) e o `tenantId` viajam no token: **o tenant sempre vem do token, nunca de parâmetro**.
- Tudo exige autenticação, exceto o login, o refresh, `GET /actuator/health` e a documentação. Sem token válido: `401 NAO_AUTENTICADO`; perfil sem permissão (`@PreAuthorize`): `403 ACESSO_NEGADO`.
- Para chamar pelo Swagger UI: faça o login, copie o `accessToken` e use **Authorize**.

## Saúde, documentação e erros

| Recurso | Endereço |
|---|---|
| Saúde (escolha: Spring Boot Actuator, só `health` exposto) | `GET /actuator/health` |
| Swagger UI | `/swagger-ui.html` |
| OpenAPI (JSON) | `/v3/api-docs` (lista também o endpoint de saúde) |

Os erros seguem a RFC 7807 (`application/problem+json`), com `code` estável e `detail` em pt-BR (locale fixo `pt_BR`):

```json
{
  "type": "about:blank",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "Há campos inválidos. Corrija os dados informados e tente novamente.",
  "code": "VALIDACAO_INVALIDA",
  "errors": [{ "field": "nome", "message": "não deve estar em branco" }]
}
```

Códigos atuais (`br.com.geoprag.api.common.error.ErrorCode`; não renomear um existente): `REQUISICAO_INVALIDA`, `VALIDACAO_INVALIDA`, `RECURSO_NAO_ENCONTRADO`, `METODO_NAO_PERMITIDO`, `FORMATO_NAO_SUPORTADO`, `ERRO_INTERNO`, `CREDENCIAIS_INVALIDAS`, `USUARIO_DESATIVADO`, `NAO_AUTENTICADO` e `ACESSO_NEGADO`. Erros inesperados nunca expõem detalhes internos. `errors[]` só aparece em `VALIDACAO_INVALIDA`.

**CORS:** `CORS_ALLOWED_ORIGINS` define as origens permitidas (por exemplo, a do portal Web local: `http://localhost:3000`). Vazio = nenhuma origem externa. O preflight `OPTIONS` é tratado, e não há cookies (autenticação por `Authorization: Bearer`).

> O Swagger e o OpenAPI ficam habilitados por padrão. Para desligá-los em produção, use `springdoc.api-docs.enabled=false` e `springdoc.swagger-ui.enabled=false`.

## Build e testes

```bash
./mvnw verify
```

A maioria dos testes usa H2 em memória (modo PostgreSQL) e não precisa de banco externo. Os testes de **integração com PostgreSQL real** (Testcontainers, hoje o envio em lote de `/sync/aplicacoes`) precisam de **Docker em execução**; sem Docker eles são **ignorados**, e o CI roda com Docker. Sem Maven nem JDK local, rode pelo Docker:

```bash
docker run --rm -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-21 mvn -B verify
```

Para incluir os testes com PostgreSQL ao rodar o Maven dentro de um contêiner, monte o socket do Docker (`-v /var/run/docker.sock:/var/run/docker.sock`) e, no Docker Desktop (Windows/macOS), acrescente `-e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -e TESTCONTAINERS_RYUK_DISABLED=true`.

## Estrutura

Pacotes **por feature** em `br.com.geoprag.api`, por exemplo `auth`, `ponto` e `sync`. Dentro de cada feature:

```
controller → service → repository (Spring Data JPA)
dto/        (separado das entidades)
```

Controllers falam apenas com services; services com repositories; DTOs nunca expõem entidades JPA.

## Integração contínua

O workflow `.github/workflows/ci.yml` (job `quality-gates`) roda `./mvnw -B verify` com Java 21 (Temurin) em todo pull request para `develop`, `main`, `release/current` e `epic/**`, e em pushes na `develop` e na `release/current` — o mesmo desenho dos demais repositórios do GeoPrag.

Fluxo de branches: `develop` é a base; cada épico tem a sua `epic/<CHAVE>-<nome>`, e as issues saem dela.
