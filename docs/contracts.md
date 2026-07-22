# Contratos do Video Manager API

Todos os timestamps usam ISO 8601 em UTC e todos os identificadores usam UUID.
Campos nao listados fazem parte de uma mudanca de contrato e devem ser acordados
entre produtores e consumidores.

## HTTP

Os endpoints de negocio exigem `Authorization: Bearer <token>`. O token deve ser
HS256, ter o issuer configurado em `JWT_ISSUER` e conter a claim `customer_id`.

| Metodo | Endpoint | Sucesso | Descricao |
| --- | --- | --- | --- |
| `POST` | `/videos` | `202` | Recebe multipart no campo `file`. |
| `GET` | `/videos?page=0&size=20` | `200` | Lista videos do cliente; `size` aceita de 1 a 100. |
| `GET` | `/videos/{videoId}` | `200` | Retorna um video pertencente ao cliente. |
| `GET` | `/videos/{videoId}/download` | `200` | Retorna `application/zip` quando o status e `PROCESSED`. |

Resposta do upload:

```json
{
  "videoId": "0f79bf2f-d8f5-4d32-861c-218f4860a681",
  "status": "PENDING_PROCESSING"
}
```

Item retornado por detalhe e listagem:

```json
{
  "videoId": "0f79bf2f-d8f5-4d32-861c-218f4860a681",
  "originalFilename": "aula.mp4",
  "status": "PROCESSED",
  "createdAt": "2026-07-22T12:00:00Z",
  "updatedAt": "2026-07-22T12:01:30Z"
}
```

A listagem envolve os itens em `content` e inclui `page`, `size`,
`totalElements` e `totalPages`.

Erros de negocio usam o formato abaixo:

```json
{
  "timestamp": "2026-07-22T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid video file",
  "path": "/videos"
}
```

Codigos esperados: `400` para upload ou paginacao invalidos, `401` para token
ausente ou invalido, `404` para video inexistente ou de outro cliente, `409`
para download ainda indisponivel e `413` para arquivo acima do limite.

## Kafka

As mensagens usam JSON e a chave Kafka e sempre `videoId`. A entrega e pelo
menos uma vez; `eventId` e a chave de idempotencia. Eventos de resultado que
esgotam os retries seguem para o topico de origem acrescido de `.dlq`.

### VideoProcessingRequested

Topico: `video.processing.requested`. Produzido pelo manager via Transactional
Outbox depois que o video foi armazenado.

```json
{
  "eventId": "66c21c68-ce76-40f4-8d93-98b1defab8c4",
  "eventType": "VideoProcessingRequested",
  "occurredAt": "2026-07-22T12:00:00Z",
  "videoId": "0f79bf2f-d8f5-4d32-861c-218f4860a681",
  "customerId": "47671cb5-f13c-48e4-bf17-6564887f8cc5",
  "originalFilename": "aula.mp4",
  "inputObjectKey": "customers/47671cb5-f13c-48e4-bf17-6564887f8cc5/videos/0f79bf2f-d8f5-4d32-861c-218f4860a681/input/aula.mp4"
}
```

### VideoProcessed

Topico: `video.processing.completed`. Consumido pelo manager para marcar o video
como processado e liberar o download.

```json
{
  "eventId": "80fbb895-e737-460c-b568-48eaf20c0b12",
  "eventType": "VideoProcessed",
  "occurredAt": "2026-07-22T12:01:30Z",
  "videoId": "0f79bf2f-d8f5-4d32-861c-218f4860a681",
  "outputObjectKey": "customers/47671cb5-f13c-48e4-bf17-6564887f8cc5/videos/0f79bf2f-d8f5-4d32-861c-218f4860a681/output/frames.zip"
}
```

### VideoProcessingFailed

Topico: `video.processing.failed`. Consumido pelo manager para marcar o video
como falho e disparar a notificacao configurada pelo cliente.

```json
{
  "eventId": "e58ef8d5-ea4b-4915-834b-d407931f6e0d",
  "eventType": "VideoProcessingFailed",
  "occurredAt": "2026-07-22T12:01:30Z",
  "videoId": "0f79bf2f-d8f5-4d32-861c-218f4860a681",
  "failureReason": "Unable to extract frames"
}
```
