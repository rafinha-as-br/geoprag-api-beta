# Ambiente local: Docker, cabo USB e `adb reverse`

Passo a passo para subir a API e o banco no Docker do notebook e usá-los pelo celular Android conectado por cabo USB. É o ambiente de teste e de demonstração do beta.

Como funciona: a API publica a porta **8080** no notebook. O `adb reverse` faz o celular enxergar essa porta como `localhost:8080`, então o app usa `http://localhost:8080` sem rede Wi-Fi. O Android 9+ bloqueia HTTP puro, e os builds de debug e profile do app liberam HTTP **somente para `localhost`** (GEOPRAG-180); o build de release público não libera.

## Pré-requisitos

- Docker e Docker Compose (veja o [README](../README.md)).
- Flutter instalado e `adb` no `PATH` (vem com o Android SDK, pasta `platform-tools`).
- Celular Android com **depuração USB** ativada (Configurações > Opções do desenvolvedor) e cabo de dados.
- O app com a leitura de `API_BASE_URL` e a configuração de rede da GEOPRAG-180 na branch que você for rodar.

## 1. Subir a API e o banco

Na raiz deste repositório:

```bash
docker compose up --build
```

Confira em outro terminal:

```bash
curl http://localhost:8080/actuator/health
```

A resposta deve conter `"status":"UP"`. Para parar: `Ctrl+C`; para apagar também os dados do banco: `docker compose down -v`.

## 2. Conectar o celular

1. Ligue o celular ao notebook pelo cabo e aceite a pergunta "Permitir depuração USB?" no aparelho.
2. Confirme que ele aparece como `device` (e não `unauthorized`):

```bash
adb devices
```

## 3. Criar o túnel

```bash
adb reverse tcp:8080 tcp:8080
adb reverse --list
```

A segunda linha deve listar `tcp:8080 tcp:8080`. O script `scripts/adb-reverse.sh` faz os dois comandos de uma vez (veja [Script](#script-opcional)).

## 4. Rodar o app no celular

Na pasta do projeto Flutter do App Aplicador (`app_aplicador/app_aplicador`):

```bash
flutter run --dart-define=API_BASE_URL=http://localhost:8080
```

O app abre na tela inicial e segue para o **login**: é esse o ponto de chegada deste guia. A URL base muda pela flag, sem alterar código.

### APK de demonstração

Para instalar um APK sem o `flutter run` (por exemplo, no ensaio ou no plano B), use o build **profile**, que é o que carrega a liberação de HTTP para `localhost`:

```bash
flutter build apk --profile --dart-define=API_BASE_URL=http://localhost:8080
adb install -r build/app/outputs/flutter-apk/app-profile.apk
```

Um build `--release` não libera HTTP puro e não alcança a API local.

## 5. Portal Web no notebook

O navegador do notebook acessa `localhost` direto, sem `adb`. Para o navegador poder chamar a API, a origem do portal precisa estar em `CORS_ALLOWED_ORIGINS`.

1. Em `.env` (copie de `.env.example`), deixe `CORS_ALLOWED_ORIGINS=http://localhost:3000` e reinicie a API (`docker compose up --build`).
2. Na pasta do projeto do portal (`portal_administrador/app_administrador`), fixe a porta para bater com a origem liberada:

```bash
flutter run -d chrome --web-port 3000 --dart-define=API_BASE_URL=http://localhost:8080
```

## Simular offline

Desconectar o cabo derruba o túnel, então o app deixa de alcançar a API: é a forma de simular offline na demonstração.

1. Com o app aberto, **desconecte o cabo USB**.
2. Use o app normalmente (registros ficam no aparelho, pendentes de envio).
3. **Reconecte o cabo** e recrie o túnel, porque ele não volta sozinho:

```bash
adb reverse tcp:8080 tcp:8080
```

4. O app volta a alcançar a API e sincroniza.

## Solução de problemas

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| App não conecta, mas a API responde no notebook | O túnel `adb reverse` sumiu | Rode `adb reverse --list`; se vazio, repita `adb reverse tcp:8080 tcp:8080` |
| O túnel some | Cabo desconectado, celular reiniciado ou servidor do ADB reiniciado (`adb kill-server`) | Repita o `adb reverse` |
| `adb devices` lista `unauthorized` | Depuração USB não autorizada | Aceite o diálogo no celular; se não aparecer, revogue as autorizações nas opções do desenvolvedor e reconecte |
| `adb devices` não lista o celular | Cabo só de carga, modo USB incorreto ou driver ausente | Troque o cabo, escolha "Transferência de arquivos" e reconecte |
| App com `--release` não alcança a API | Release público não libera HTTP puro (de propósito) | Use `flutter run` ou o build `--profile` |
| `curl` no notebook falha | API não subiu ou a porta publicada é outra | `docker compose ps`; confira em `docker-compose.yml` a porta publicada (`8080:8080`) e use a mesma no `adb reverse` |
| Portal Web recebe erro de CORS | Origem do portal fora de `CORS_ALLOWED_ORIGINS` | Ajuste o `.env`, reinicie a API e use `--web-port 3000` |

## Script opcional

`scripts/adb-reverse.sh` recria o túnel com um comando (a porta padrão é 8080; passe outra como argumento):

```bash
./scripts/adb-reverse.sh
./scripts/adb-reverse.sh 8081
```
