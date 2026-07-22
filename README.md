# Video Manager API

Microservico da FIAP X responsavel por receber videos, controlar o ciclo de
processamento e disponibilizar status e resultado ao cliente.

## Visao geral

O servico usa Java 21, Kotlin, Spring Boot, PostgreSQL, MinIO e Kafka. A gravacao
do video e do pedido de processamento usa Transactional Outbox; os resultados
sao consumidos de forma idempotente, com retry e DLQ.

```text
RECEIVED -> STORED -> PENDING_PROCESSING -> PROCESSING -> PROCESSED
```

Qualquer estado nao terminal pode mudar para `FAILED`. O codigo e organizado em:

```text
src/main/kotlin/com/fiap/hackathon/videomanagerapi/
  domain/video/          regras e modelos de dominio
  application/video/     casos de uso e portas
  infrastructure/video/  HTTP, persistencia, storage e mensageria
```

O dominio nao depende de Spring; a aplicacao coordena casos de uso; a
infraestrutura conecta HTTP, PostgreSQL, Kafka, MinIO e notificacoes.

## Inicio rapido com Docker

Requisitos: Git e Docker com Compose. Na raiz do projeto, execute:

```bash
docker compose up --build -d
docker compose ps
curl http://localhost:8082/actuator/health/readiness
```

O Compose constroi a API e inicia PostgreSQL, MinIO e Kafka. Aguarde a readiness
retornar `UP`. A API fica em `http://localhost:8082` e o console do MinIO em
`http://localhost:9001`. Para acompanhar ou remover o ambiente:

```bash
docker compose logs -f video-manager-api
docker compose down
```

Volumes persistem os dados entre reinicios. Use `docker compose down -v` apenas
quando quiser apagar todos os dados locais.

## Execucao pela JVM

Requisitos: Java 21 e Docker. O Gradle e fornecido pelo Wrapper.

```bash
docker compose up -d postgres-video minio kafka
./gradlew bootRun
```

A aplicacao usa a porta `8082` e cria os buckets do MinIO de forma idempotente.
Flyway cria e atualiza o schema; Hibernate apenas valida o schema.

## Autenticacao e uso

Os endpoints de negocio exigem um token HS256 emitido pelo
`customer-auth-api`. Os servicos devem compartilhar `JWT_SECRET` e `JWT_ISSUER`,
e o token deve conter `customer_id` como UUID.

```bash
export ACCESS_TOKEN='token-emitido-pelo-customer-auth-api'

curl -X POST http://localhost:8082/videos \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F 'file=@video.mp4;type=video/mp4'

curl 'http://localhost:8082/videos?page=0&size=20' \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl http://localhost:8082/videos/VIDEO_ID \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl http://localhost:8082/videos/VIDEO_ID/download \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  --output frames.zip
```

O upload retorna `202 Accepted` com status `PENDING_PROCESSING`. O download fica
disponivel em `PROCESSED`; antes disso retorna `409 Conflict`. Videos ausentes ou
de outro cliente retornam `404` sem revelar sua existencia.

Os contratos completos de endpoints, erros, topicos e payloads Kafka estao em
[docs/contracts.md](docs/contracts.md).

## Variaveis de ambiente

Os valores abaixo sao defaults de desenvolvimento. No EKS, injete credenciais
por Kubernetes Secrets ou por um gerenciador externo; nao use os defaults locais.

| Variavel | Padrao | Finalidade |
| --- | --- | --- |
| `SERVER_PORT` | `8082` | Porta HTTP da API. |
| `MANAGEMENT_SERVER_PORT` | valor de `SERVER_PORT` | Porta interna do Actuator. |
| `DB_URL` | `jdbc:postgresql://localhost:5434/video_db` | JDBC do PostgreSQL. |
| `DB_USERNAME` | `fiapx` | Usuario do banco. |
| `DB_PASSWORD` | `fiapx` | Senha do banco. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers Kafka. |
| `KAFKA_PROCESSING_RESULTS_GROUP_ID` | `video-manager-processing-results` | Grupo dos resultados. |
| `MINIO_ENDPOINT` | `http://localhost:9000` | Endpoint S3 compativel. |
| `MINIO_ACCESS_KEY` | `fiapx` | Access key do storage. |
| `MINIO_SECRET_KEY` | `fiapx12345` | Secret key do storage. |
| `MINIO_INPUT_BUCKET` | `fiapx-videos-input` | Bucket de entrada. |
| `MINIO_OUTPUT_BUCKET` | `fiapx-videos-output` | Bucket de saida. |
| `JWT_SECRET` | segredo local | Segredo HS256 compartilhado. |
| `JWT_ISSUER` | `customer-auth-api` | Issuer aceito. |
| `VIDEO_MAX_FILE_SIZE` | `500MB` | Limite do arquivo. |
| `VIDEO_MAX_REQUEST_SIZE` | `501MB` | Limite da requisicao multipart. |
| `OUTBOX_SCHEDULING_ENABLED` | `true` | Habilita o dispatcher. |
| `OUTBOX_FIXED_DELAY` | `1s` | Intervalo do dispatcher. |
| `OUTBOX_BATCH_SIZE` | `50` | Eventos por lote. |
| `OUTBOX_RETRY_DELAY` | `5s` | Espera apos falha de envio. |
| `OUTBOX_SEND_TIMEOUT` | `10s` | Timeout de confirmacao Kafka. |
| `PROCESSING_RESULTS_RETRY_INTERVAL` | `1s` | Intervalo entre retries do consumer. |
| `PROCESSING_RESULTS_MAX_RETRIES` | `2` | Retries antes da DLQ. |
| `CUSTOMER_AUTH_API_URL` | `http://localhost:8081` | URL interna do customer auth. |
| `INTERNAL_API_KEY` | chave local | Chave da comunicacao interna. |
| `CUSTOMER_AUTH_CONNECT_TIMEOUT` | `500ms` | Timeout de conexao. |
| `CUSTOMER_AUTH_READ_TIMEOUT` | `1s` | Timeout de leitura. |
| `CUSTOMER_AUTH_RETRY_MAX_ATTEMPTS` | `3` | Tentativas da consulta interna. |
| `CUSTOMER_AUTH_RETRY_DELAY` | `100ms` | Espera entre tentativas. |
| `CUSTOMER_AUTH_CIRCUIT_WINDOW_SIZE` | `5` | Janela do circuit breaker. |
| `CUSTOMER_AUTH_CIRCUIT_MINIMUM_CALLS` | `3` | Chamadas minimas para avaliar falhas. |
| `CUSTOMER_AUTH_CIRCUIT_FAILURE_RATE` | `50` | Percentual que abre o circuito. |
| `CUSTOMER_AUTH_CIRCUIT_OPEN_DURATION` | `10s` | Duracao do circuito aberto. |
| `MAIL_HOST` | `localhost` | Host SMTP. |
| `MAIL_PORT` | `1025` | Porta SMTP. |
| `MAIL_USERNAME` | vazio | Usuario SMTP. |
| `MAIL_PASSWORD` | vazio | Senha SMTP. |
| `MAIL_CONNECTION_TIMEOUT` | `1000` | Conexao SMTP em milissegundos. |
| `MAIL_READ_TIMEOUT` | `1000` | Leitura SMTP em milissegundos. |
| `MAIL_WRITE_TIMEOUT` | `1000` | Escrita SMTP em milissegundos. |
| `NOTIFICATION_EMAIL_FROM` | `no-reply@fiapx.local` | Remetente das notificacoes. |
| `TELEGRAM_BOT_TOKEN` | vazio | Token do bot Telegram. |
| `TELEGRAM_CONNECT_TIMEOUT` | `500ms` | Conexao com Telegram. |
| `TELEGRAM_READ_TIMEOUT` | `1s` | Leitura do Telegram. |
| `OBSERVABILITY_HEALTH_TIMEOUT` | `2s` | Timeout dos health indicators. |
| `SHUTDOWN_TIMEOUT` | `30s` | Prazo do encerramento gracioso. |

## Build, testes e imagem

```bash
./gradlew build
docker build -t video-manager-api:local .
```

A suite usa Testcontainers para subir PostgreSQL, MinIO e Kafka reais. Ela cobre
dominio, adaptadores e o fluxo vertical entre upload HTTP, persistencia, storage,
outbox, resultado Kafka, consulta e download.

O GitHub Actions executa o build completo e constroi a imagem em todo push e pull
request. A imagem final usa Java 21 JRE, usuario nao-root e nao contem ferramentas
de build.

## Kafka e notificacoes

O pedido `VideoProcessingRequested` e persistido junto com o video e publicado
em `video.processing.requested`. O manager consome `VideoProcessed` de
`video.processing.completed` e `VideoProcessingFailed` de
`video.processing.failed`. Mensagens que esgotam os retries seguem para o topico
de origem com sufixo `.dlq`.

Falhas de processamento sao confirmadas no banco antes da notificacao. A
preferencia do cliente vem do `customer-auth-api`; os canais suportados sao
e-mail e Telegram. Falhas definitivas de notificacao sao registradas sem mudar o
resultado do processamento.

## Observabilidade e EKS

Os endpoints de operacao sao publicos dentro da rede do servico:

```bash
curl http://localhost:8082/actuator/health/liveness
curl http://localhost:8082/actuator/health/readiness
curl http://localhost:8082/actuator/prometheus
```

Readiness verifica PostgreSQL, Kafka e MinIO. No EKS, configure uma porta de
management separada e mantenha Actuator fora do Ingress publico:

```yaml
env:
  - name: SERVER_PORT
    value: "8082"
  - name: MANAGEMENT_SERVER_PORT
    value: "8083"
ports:
  - name: http
    containerPort: 8082
  - name: management
    containerPort: 8083
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: management
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: management
```

O shutdown e gracioso para respeitar a terminacao de pods. Logs sao JSON no
formato ECS e metricas Prometheus incluem uploads, outbox, resultados e
notificacoes sem usar IDs como tags.
