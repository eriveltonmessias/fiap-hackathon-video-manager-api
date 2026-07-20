package com.fiap.hackathon.videomanagerapi.infrastructure.security

import com.fiap.hackathon.videomanagerapi.TestcontainersConfiguration
import com.fiap.hackathon.videomanagerapi.application.video.AuthenticatedCustomerProvider
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(properties = ["app.security.jwt-secret=test-jwt-secret-with-at-least-thirty-two-bytes"])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, CustomerIdTestConfiguration::class)
class SecurityIntegrationTest(
	@Autowired private val mockMvc: MockMvc,
) {
	@Test
	fun `health check is public`() {
		mockMvc.get("/actuator/health")
			.andExpect { status { isOk() } }
	}

	@Test
	fun `protected endpoint rejects missing token`() {
		mockMvc.get("/test/customer-id")
			.andExpect { status { isUnauthorized() } }
	}

	@Test
	fun `protected endpoint rejects token with invalid signature`() {
		val token = token(
			secret = "different-test-jwt-secret-with-at-least-32-bytes",
			expiresAt = Instant.now().plusSeconds(3600),
		)

		mockMvc.get("/test/customer-id") {
			header("Authorization", "Bearer $token")
		}.andExpect { status { isUnauthorized() } }
	}

	@Test
	fun `protected endpoint rejects expired token`() {
		val token = token(
			issuedAt = Instant.now().minusSeconds(7200),
			expiresAt = Instant.now().minusSeconds(3600),
		)

		mockMvc.get("/test/customer-id") {
			header("Authorization", "Bearer $token")
		}.andExpect { status { isUnauthorized() } }
	}

	@Test
	fun `protected endpoint rejects token with invalid customer id claim`() {
		val token = token(
			customerIdClaim = "not-a-uuid",
			expiresAt = Instant.now().plusSeconds(3600),
		)

		mockMvc.get("/test/customer-id") {
			header("Authorization", "Bearer $token")
		}.andExpect { status { isUnauthorized() } }
	}

	@Test
	fun `valid token makes customer id available to application`() {
		val customerId = UUID.fromString("77320f9e-e4e9-4792-a3bf-9ff22dbafb2c")
		val token = token(customerId = customerId, expiresAt = Instant.now().plusSeconds(3600))

		mockMvc.get("/test/customer-id") {
			header("Authorization", "Bearer $token")
		}.andExpect {
			status { isOk() }
			jsonPath("$.customerId") { value(customerId.toString()) }
		}
	}

	@Test
	fun `non-health actuator endpoint requires authentication`() {
		mockMvc.get("/actuator/info")
			.andExpect { status { isUnauthorized() } }
	}

	private fun token(
		customerId: UUID = UUID.randomUUID(),
		customerIdClaim: String = customerId.toString(),
		secret: String = JWT_SECRET,
		issuedAt: Instant = Instant.now(),
		expiresAt: Instant,
	): String {
		val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
		val encoder = NimbusJwtEncoder(ImmutableSecret(key))
		val claims = JwtClaimsSet.builder()
			.issuer(JWT_ISSUER)
			.subject(customerId.toString())
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.claim(CustomerIdClaimValidator.CUSTOMER_ID_CLAIM, customerIdClaim)
			.build()
		return encoder.encode(
			JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims),
		).tokenValue
	}

	private companion object {
		const val JWT_SECRET = "test-jwt-secret-with-at-least-thirty-two-bytes"
		const val JWT_ISSUER = "customer-auth-api"
	}
}

@TestConfiguration(proxyBeanMethods = false)
class CustomerIdTestConfiguration {
	@Bean
	fun customerIdTestController(provider: AuthenticatedCustomerProvider) = CustomerIdTestController(provider)
}

@RestController
class CustomerIdTestController(
	private val provider: AuthenticatedCustomerProvider,
) {
	@GetMapping("/test/customer-id")
	fun customerId(): Map<String, String> = mapOf("customerId" to provider.customerId().toString())
}
