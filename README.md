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

Isso sobe o PostgreSQL e a API (porta **8080**). Na primeira inicialização o Flyway executa as migrations do esquema (`V1` vazia e `V2` com `tenant` e `usuario`) e, no perfil `local` (padrão do Compose), o seed fictício descrito abaixo.

Para customizar a configuração, copie o exemplo e ajuste (o `.env` é ignorado pelo Git):

```bash
cp .env.example .env
```

Sem `.env`, o Compose usa valores padrão de desenvolvimento local (o banco não é publicado fora da rede do Compose). **Esses padrões servem só para o ambiente local**: em qualquer ambiente compartilhado, defina `DB_PASSWORD`, `JWT_SECRET` e `CORS_ALLOWED_ORIGINS` no `.env`.

## Configuração

Toda a configuração vem **somente de variáveis de ambiente** (veja `.env.example`):

| Variável | Uso |
|---|---|
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Banco de dados (no Compose, `DB_URL` é montada a partir delas) |
| `DB_URL` | URL JDBC. Ao rodar a API fora do Compose, `DB_URL`, `DB_USER` e `DB_PASSWORD` são obrigatórias |
| `JWT_SECRET`, `JWT_ACCESS_TOKEN_MINUTES` | Autenticação (usadas pelas features de login) |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas (lista separada por vírgula) |
| `SPRING_PROFILES_ACTIVE` | Perfil do Spring. `local` (padrão do Compose) carrega o seed fictício; sem ele o seed não existe |

> O repositório é público: nunca versione `.env`, chaves ou credenciais reais.

## Usuários, senhas e seed

- **Esquema** (`db/migration`, `V2`): `tenant` (id textual, ex.: `gaspar-sc`) e `usuario` (perfil `ADMINISTRADOR`, `SUB_ADMINISTRADOR` ou `APLICADOR`; status `ATIVO` ou `DESATIVADO`; `nome`, `e-mail`, `cpf`, hash de senha e `tenant_id`). O SQL é portável entre PostgreSQL e H2.
- **Senha:** só o hash BCrypt é guardado (`spring-security-crypto`; o Spring Security completo entra com o login).
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

Códigos atuais (`br.com.geoprag.api.common.error.ErrorCode`; não renomear um existente): `REQUISICAO_INVALIDA`, `VALIDACAO_INVALIDA`, `RECURSO_NAO_ENCONTRADO`, `METODO_NAO_PERMITIDO`, `FORMATO_NAO_SUPORTADO` e `ERRO_INTERNO`. Erros inesperados nunca expõem detalhes internos. `errors[]` só aparece em `VALIDACAO_INVALIDA`.

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
