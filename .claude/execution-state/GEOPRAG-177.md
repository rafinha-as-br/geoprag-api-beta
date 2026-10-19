# Execution State — GEOPRAG-177

## Estado
EM_EXECUÇÃO

## Objetivo atual / contexto de retomada
Erro padronizado (ProblemDetail RFC 7807 com code estável e errors[], pt-BR), Swagger (springdoc), CORS por variável de ambiente e endpoint de saúde (actuator) no geoprag-api-beta. Base/destino do PR: epic/GEOPRAG-170-esqueleto-api (d9a606d). Tentativa 1, branch feat/GEOPRAG-177-claude.

## Próxima ação
Implementar (common/error, common/cors, dependências, application.yml, testes) e rodar mvn verify via container Maven.

## Não repetir
- Não usar git push simples antes de git push -u origin feat/GEOPRAG-177-claude (a branch foi criada sem tracking da epic).

## Decisões técnicas
- Saúde em /actuator/health (actuator, só health exposto); springdoc com show-actuator=true para listá-lo no Swagger.
- Locale fixo pt_BR (mensagens de validação em português).

## Bloqueios / decisões pendentes de Rafinha
- Nenhum

## Última atualização
2026-10-19
