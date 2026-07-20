package com.fiap.hackathon.videomanagerapi.infrastructure.outbox

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface SpringDataOutboxEventRepository : JpaRepository<OutboxEventJpaEntity, UUID> {
	@Query(
		value = """
			SELECT *
			FROM outbox_events
			WHERE published_at IS NULL
			  AND next_attempt_at <= :now
			ORDER BY occurred_at, id
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
		""",
		nativeQuery = true,
	)
	fun lockPending(
		@Param("now") now: Instant,
		@Param("batchSize") batchSize: Int,
	): List<OutboxEventJpaEntity>
}
