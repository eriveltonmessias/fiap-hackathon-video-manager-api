# Video Manager API

Microservico da solucao FIAP X responsavel por receber videos, controlar o ciclo de processamento e disponibilizar status e resultado ao cliente.

## Status

O servico possui atualmente:

- Spring Boot com Kotlin;
- Gradle Kotlin DSL;
- Java 21;
- health checks de liveness e readiness com Actuator;
- metricas Prometheus e logs estruturados no formato ECS;
- separacao inicial em camadas;
- agregado de dominio `VideoProcessing`;
- value objects para nomes, chaves de objetos e motivo de falha;
- maquina de estados do processamento;
- persistencia PostgreSQL com Spring Data JPA;
- versionamento do schema com Flyway;
- validacao JWT com OAuth2 Resource Server;
- identificacao do cliente pela claim `customer_id`;
- storage S3 compativel com MinIO;
- criacao automatica dos buckets de entrada e saida;
- upload multipart autenticado de videos;
- consulta paginada e detalhe de videos isolados por cliente;
- publicacao assincrona com Kafka e Transactional Outbox;
- consumo idempotente dos resultados de processamento com retry e DLQ;
- download autenticado do ZIP gerado para o proprietario do video;
- notificacao de falhas por e-mail ou Telegram conforme preferencia do cliente;
- fluxo de integracao completo entre HTTP, PostgreSQL, MinIO e Kafka;
- testes de integracao com PostgreSQL, MinIO e Kafka reais via Testcontainers;
- testes das invariantes e transicoes de dominio.

## Ciclo do processamento

O agregado controla as seguintes transicoes:

```text
RECEIVED -> STORED -> PENDING_PROCESSING -> PROCESSING -> PROCESSED
```

Qualquer estado nao terminal pode transicionar para `FAILED`. Estados terminais nao aceitam novas transicoes.

## Arquitetura

O codigo utiliza tres camadas-base agrupadas pelo subdominio `video`:

```text
src/main/kotlin/com/fiap/hackathon/videomanagerapi/
  domain/video/          regras e modelos de dominio
  application/video/     casos de uso e portas
  infrastructure/video/  HTTP, persistencia, storage e mensageria
```

Regras de dependencia:

- `domain` nao depende de Spring ou infraestrutura.
- `application` coordena casos de uso e depende do dominio.
- `infrastructure` conecta a aplicacao a HTTP, banco, Kafka e MinIO.

## Requisitos

- Java 21
- Git
- Docker

O projeto usa Gradle Wrapper, portanto nao e necessario instalar Gradle globalmente.

## Banco de dados local

Inicie o PostgreSQL na porta `5434`:

```bash
docker compose up -d postgres-video
```

O Flyway cria e atualiza o schema na inicializacao da aplicacao. O Hibernate usa
`ddl-auto=validate` e nao altera o schema.

Configuracao padrao:

```text
DB_URL=jdbc:postgresql://localhost:5434/video_db
DB_USERNAME=fiapx
DB_PASSWORD=fiapx
```

Esses valores podem ser sobrescritos por variaveis de ambiente.

## Storage local

Inicie o MinIO nas portas `9000` (API) e `9001` (console):

```bash
docker compose up -d minio
```

A aplicacao cria os buckets `fiapx-videos-input` e `fiapx-videos-output` de
forma idempotente durante a inicializacao.

Configuracao padrao:

```text
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=fiapx
MINIO_SECRET_KEY=fiapx12345
MINIO_INPUT_BUCKET=fiapx-videos-input
MINIO_OUTPUT_BUCKET=fiapx-videos-output
```

## Seguranca

A API valida os tokens HS256 emitidos pelo `customer-auth-api`. Os dois servicos
devem compartilhar o mesmo segredo e issuer:

```text
JWT_SECRET=local-development-jwt-secret-change-me-1234567890
JWT_ISSUER=customer-auth-api
```

Os endpoints `GET /actuator/health`, seus subpaths e `GET /actuator/prometheus`
sao publicos. Os endpoints de negocio exigem um token Bearer valido com a claim
`customer_id` no formato UUID.

## Upload de video

Envie o arquivo no campo multipart `file`:

```bash
curl -X POST http://localhost:8082/videos \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -F 'file=@video.mp4;type=video/mp4'
```

Um upload valido retorna `202 Accepted` com o identificador e o status
`PENDING_PROCESSING`.
Por padrao, o arquivo deve ter ate `500MB`, nao pode estar vazio e deve possuir
extensao e tipo de video suportados. O limite pode ser alterado com
`VIDEO_MAX_FILE_SIZE` e `VIDEO_MAX_REQUEST_SIZE`.

## Consulta de videos

Liste os videos do cliente autenticado:

```bash
curl 'http://localhost:8082/videos?page=0&size=20' \
  -H 'Authorization: Bearer ACCESS_TOKEN'
```

Consulte um video especifico:

```bash
curl http://localhost:8082/videos/VIDEO_ID \
  -H 'Authorization: Bearer ACCESS_TOKEN'
```

A listagem aceita paginas a partir de `0` e tamanho entre `1` e `100`. Videos
de outro cliente retornam o mesmo `404` de um identificador inexistente. As
respostas nao expoem as chaves internas dos objetos no MinIO.

## Download do resultado

Depois que o video atingir o status `PROCESSED`, baixe o ZIP gerado:

```bash
curl http://localhost:8082/videos/VIDEO_ID/download \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  --output frames.zip
```

O arquivo e transmitido diretamente pelo manager como `application/zip`, sem
expor a chave interna do MinIO. Videos ainda nao processados retornam
`409 Conflict`; videos inexistentes ou pertencentes a outro cliente retornam `404`.

## Kafka e Outbox

Inicie o broker Kafka na porta `9092`:

```bash
docker compose up -d kafka
```

Depois do upload no MinIO, o registro do video e o evento
`VideoProcessingRequested` sao gravados na mesma transacao PostgreSQL. O
dispatcher publica eventos pendentes no topico `video.processing.requested` e
so preenche `published_at` depois da confirmacao do Kafka.

Se a publicacao falhar, a tentativa e registrada e o evento permanece pendente
para retry. A entrega e pelo menos uma vez; consumidores devem usar `eventId`
para tratar repeticoes de forma idempotente.

O manager consome `VideoProcessed` de `video.processing.completed` e
`VideoProcessingFailed` de `video.processing.failed`. Cada `eventId` processado e
registrado na mesma transacao que atualiza o video. Mensagens que continuam
falhando depois dos retries sao publicadas no topico de origem com o sufixo
`.dlq`.

Configuracao principal:

```text
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
OUTBOX_FIXED_DELAY=1s
OUTBOX_BATCH_SIZE=50
OUTBOX_RETRY_DELAY=5s
OUTBOX_SEND_TIMEOUT=10s
PROCESSING_RESULTS_RETRY_INTERVAL=1s
PROCESSING_RESULTS_MAX_RETRIES=2
```

## Notificacao de falha

Ao consumir `VideoProcessingFailed`, o manager confirma o estado `FAILED` no
banco antes de consultar a preferencia do cliente. A consulta ao
`customer-auth-api` usa chave interna, timeout, retry limitado e circuit breaker.

O canal `EMAIL` usa SMTP e o canal `TELEGRAM` usa a Bot API. Se a preferencia ou
o envio falhar definitivamente, o erro seguro e registrado em
`notification_failures` sem alterar o resultado do processamento.

Configuracao principal:

```text
CUSTOMER_AUTH_API_URL=http://localhost:8081
INTERNAL_API_KEY=local-internal-api-key
CUSTOMER_AUTH_CONNECT_TIMEOUT=500ms
CUSTOMER_AUTH_READ_TIMEOUT=1s
CUSTOMER_AUTH_RETRY_MAX_ATTEMPTS=3
MAIL_HOST=localhost
MAIL_PORT=1025
NOTIFICATION_EMAIL_FROM=no-reply@fiapx.local
TELEGRAM_BOT_TOKEN=
```

## Executar testes

```bash
./gradlew test
```

A suite usa Testcontainers para subir PostgreSQL, MinIO e Kafka. O teste vertical
executa upload HTTP, persistencia, armazenamento, publicacao outbox, retorno do
processamento por Kafka, consulta e download, incluindo evento duplicado, `401`,
`404` entre clientes e arquivo invalido.

## Executar a aplicacao

```bash
./gradlew bootRun
```

A aplicacao usa a porta `8082` por padrao. Para alterar:

```bash
SERVER_PORT=8092 ./gradlew bootRun
```

## Health check

Liveness verifica somente se a aplicacao esta ativa:

```bash
curl http://localhost:8082/actuator/health/liveness
```

Readiness inclui PostgreSQL, Kafka e MinIO e retorna `DOWN` quando alguma dessas
dependencias nao esta disponivel:

```bash
curl http://localhost:8082/actuator/health/readiness
```

O timeout dos indicadores externos e configurado por
`OBSERVABILITY_HEALTH_TIMEOUT`, com valor padrao de `2s`.

No EKS, configure os probes do container com estes paths:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: management
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: management
```

`MANAGEMENT_SERVER_PORT` permite publicar Actuator em uma porta interna separada
da API. O Service de monitoramento pode acessar essa porta, enquanto o Ingress
publico deve encaminhar somente a porta da aplicacao. O encerramento e gracioso,
com prazo configuravel por `SHUTDOWN_TIMEOUT` e padrao de `30s`.

## Metricas e logs

As metricas no formato Prometheus ficam disponiveis em:

```bash
curl http://localhost:8082/actuator/prometheus
```

No EKS, o scraper deve acessar esse endpoint pela porta de management dentro do
cluster; o path nao deve ser publicado pelo Ingress externo.

Metricas de negocio:

- `video_manager_uploads_total` e tamanho dos uploads;
- `video_manager_outbox_events_total` por resultado e topico;
- `video_manager_processing_results_total` por tipo e resultado;
- `video_manager_notifications_total` por canal e resultado.

Os identificadores nao sao usados como tags de metricas. Os logs de runtime sao
JSON no formato ECS e incluem `customerId`, `videoId` e `eventId` nos pontos do
ciclo em que estao disponiveis. Erros externos registram apenas o tipo seguro da
falha, sem mensagens, tokens ou credenciais.

## Build

```bash
./gradlew build
```

O JAR executavel sera gerado em `build/libs/`.

## Proxima task

A proxima task sera container, CI e documentacao final na branch:

```text
feature/delivery-pipeline
```

Essa branch somente deve ser criada depois do merge de `feature/integration-flow-tests` na `main`.
