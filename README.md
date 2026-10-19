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

Isso sobe o PostgreSQL e a API (porta **8080**). Na primeira inicialização o Flyway executa a migration `V1` (vazia).

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

> O repositório é público: nunca versione `.env`, chaves ou credenciais reais.

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
