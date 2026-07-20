# Video Manager API

Microservico da solucao FIAP X responsavel por receber videos, controlar o ciclo de processamento e disponibilizar status e resultado ao cliente.

## Status

O servico possui atualmente:

- Spring Boot com Kotlin;
- Gradle Kotlin DSL;
- Java 21;
- health check com Actuator;
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
- testes de integracao com PostgreSQL e MinIO reais via Testcontainers;
- testes das invariantes e transicoes de dominio.

Kafka sera adicionado em branch propria.

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

Somente `GET /actuator/health` e publico. Os demais endpoints exigem um token
Bearer valido com a claim `customer_id` no formato UUID.

## Upload de video

Envie o arquivo no campo multipart `file`:

```bash
curl -X POST http://localhost:8082/videos \
  -H 'Authorization: Bearer ACCESS_TOKEN' \
  -F 'file=@video.mp4;type=video/mp4'
```

Um upload valido retorna `202 Accepted` com o identificador e o status `STORED`.
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

## Executar testes

```bash
./gradlew test
```

## Executar a aplicacao

```bash
./gradlew bootRun
```

A aplicacao usa a porta `8082` por padrao. Para alterar:

```bash
SERVER_PORT=8092 ./gradlew bootRun
```

## Health check

Com a aplicacao em execucao:

```bash
curl http://localhost:8082/actuator/health
```

Resposta esperada:

```json
{
  "status": "UP"
}
```

## Build

```bash
./gradlew build
```

O JAR executavel sera gerado em `build/libs/`.

## Proxima task

A proxima task sera a publicacao transacional via Kafka e Outbox na branch:

```text
feature/video-processing-outbox
```

Essa branch somente deve ser criada depois do merge de `feature/video-query` na `main`.
