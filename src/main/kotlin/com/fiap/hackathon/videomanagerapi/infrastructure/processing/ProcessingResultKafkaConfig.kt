package com.fiap.hackathon.videomanagerapi.infrastructure.processing

import org.apache.kafka.common.TopicPartition
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.CommonErrorHandler
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
@EnableConfigurationProperties(ProcessingResultProperties::class)
class ProcessingResultKafkaConfig {
	@Bean
	fun processingResultErrorHandler(
		kafkaTemplate: KafkaTemplate<String, String>,
		properties: ProcessingResultProperties,
	): CommonErrorHandler {
		val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
			TopicPartition("${record.topic()}.dlq", record.partition())
		}.apply {
			setFailIfSendResultIsError(true)
		}
		return DefaultErrorHandler(
			recoverer,
			FixedBackOff(properties.retryInterval.toMillis(), properties.maxRetries),
		)
	}
}
