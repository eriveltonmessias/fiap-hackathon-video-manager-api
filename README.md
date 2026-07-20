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
- testes de integracao com PostgreSQL real via Testcontainers;
- testes das invariantes e transicoes de dominio.

Seguranca, MinIO e Kafka serao adicionados em branches proprias.

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

A proxima task sera a seguranca JWT na branch:

```text
feature/jwt-security
```

Essa branch somente deve ser criada depois do merge de `feature/video-persistence` na `main`.
