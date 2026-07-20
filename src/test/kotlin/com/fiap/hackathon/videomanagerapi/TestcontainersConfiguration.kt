package com.fiap.hackathon.videomanagerapi

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
	@Bean
	@ServiceConnection
	fun postgresContainer(): PostgreSQLContainer =
		PostgreSQLContainer(DockerImageName.parse("postgres:16"))

	@Bean(initMethod = "start", destroyMethod = "stop")
	fun minioContainer(): MinioTestContainer = MinioTestContainer(
		DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"),
	).withExposedPorts(MINIO_API_PORT)
		.withEnv("MINIO_ROOT_USER", "fiapx")
		.withEnv("MINIO_ROOT_PASSWORD", "fiapx12345")
		.withCommand("server", "/data")
		.waitingFor(Wait.forHttp("/minio/health/live").forPort(MINIO_API_PORT))

	@Bean
	fun minioProperties(container: MinioTestContainer): DynamicPropertyRegistrar = DynamicPropertyRegistrar { registry ->
		registry.add("app.storage.minio.endpoint") {
			"http://${container.host}:${container.getMappedPort(MINIO_API_PORT)}"
		}
		registry.add("app.storage.minio.access-key") { "fiapx" }
		registry.add("app.storage.minio.secret-key") { "fiapx12345" }
		registry.add("app.outbox.scheduling-enabled") { "false" }
	}

	private companion object {
		const val MINIO_API_PORT = 9000
	}
}

class MinioTestContainer(imageName: DockerImageName) : GenericContainer<MinioTestContainer>(imageName)
