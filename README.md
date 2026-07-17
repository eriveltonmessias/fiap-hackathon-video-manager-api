# Video Manager API

Microservico da solucao FIAP X responsavel por receber videos, controlar o ciclo de processamento e disponibilizar status e resultado ao cliente.

## Status

Esta branch contem somente a estrutura inicial do servico:

- Spring Boot com Kotlin;
- Gradle Kotlin DSL;
- Java 21;
- health check com Actuator;
- separacao inicial em camadas;
- teste de inicializacao do contexto.

Dominio, banco de dados, seguranca, MinIO e Kafka serao adicionados em branches proprias.

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

Docker sera necessario nas proximas tasks, quando PostgreSQL, MinIO e Kafka forem adicionados.

O projeto usa Gradle Wrapper, portanto nao e necessario instalar Gradle globalmente.

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

A proxima task sera a modelagem do dominio de processamento de video na branch:

```text
feature/video-domain
```

Essa branch somente deve ser criada depois do merge de `feature/project-structure` na `main`.
