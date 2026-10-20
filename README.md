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

## Produto (beta)

- **Esquema** (`V4`): `produto` (`id` gerado pelo servidor, `nome`, `tenant_id`). No beta há **um único produto pré-cadastrado por tenant**, que o aplicador seleciona ao registrar uma aplicação; não há CRUD, lote, estoque nem foto. O `id` é o `produtoId` do contrato v0.
- **Seed** (`R__30`, mesmos perfis `local` e `test`, depois do seed de tenant e usuários): o produto fictício `Larvicida (exemplo)` no tenant `gaspar-sc`.
- O repositório (`produto.repository`) estende `Repository` (não `JpaRepository`): toda consulta recebe o `tenantId` do token. A tabela tem `UNIQUE (id, tenant_id)` para servir de alvo das chaves compostas do registro de aplicação.

## Registro de aplicação (serviço)

O registro de um item (subponto) enviado pelo app é feito pelo `RegistroAplicacaoService` (os endpoints `POST /sync/aplicacoes` e `GET /sync/pull` são das GEOPRAG-201 e 200). Esquema em `V6`: `aplicacao` (agrupa os subpontos, `EM_ANDAMENTO` ou `CONCLUIDA`) e `subponto` (um por item), ambos com `tenant_id` e chaves compostas.

- **Idempotente:** o `id` do subponto é o UUID gerado no app e é a chave primária; reenviar o mesmo `id` devolve `DUPLICADO` e não altera nada.
- **Append-only:** subpontos nunca são editados nem apagados (sem setters, colunas não atualizáveis e repositório sem `delete`).
- **Datas:** `realizado_em` é o relógio do aparelho (com offset); `recebido_em` é sempre do servidor. O aplicador e o tenant vêm do token.
- **Rejeita só o inválido de fato** (`DADOS_INVALIDOS`, `PONTO_INEXISTENTE`, `PRODUTO_INEXISTENTE`), sem gravar nada. Dados inconsistentes incluem data agendada de outro ponto e `aplicacaoId` reutilizado com outro ponto, data, produto ou aplicador.
- **Conflito é aceito e marcado** com um motivo (`PONTO_REATRIBUIDO`, `PONTO_DESATIVADO`, `PONTO_INATIVO`, `DATA_JA_ATENDIDA`, `DATA_CANCELADA`; vale o primeiro da lista quando há mais de um). O resultado ao app não revela o conflito; a marca fica consultável por ponto (`SubpontoRepository`), para o gestor.
- **Conclusão:** ao atingir a quantidade de subpontos do ponto, a aplicação é concluída e a data agendada recebe baixa (se nenhuma outra aplicação disputa a mesma data).

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

Os testes usam H2 em memória (modo PostgreSQL) e não precisam de banco externo. Sem Maven nem JDK local, rode pelo Docker:

```bash
docker run --rm -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-21 mvn -B verify
```

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
