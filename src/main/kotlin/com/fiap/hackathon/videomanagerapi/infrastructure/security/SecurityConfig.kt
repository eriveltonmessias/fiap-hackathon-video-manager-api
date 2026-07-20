package com.fiap.hackathon.videomanagerapi.infrastructure.security

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableConfigurationProperties(SecurityProperties::class)
class SecurityConfig {
	@Bean
	fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
		http
			.csrf { it.disable() }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests {
				it.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				it.anyRequest().authenticated()
			}
			.oauth2ResourceServer { it.jwt {} }
		return http.build()
	}

	@Bean
	fun jwtSecretKey(properties: SecurityProperties): SecretKey {
		val secretBytes = properties.jwtSecret.toByteArray(StandardCharsets.UTF_8)
		require(secretBytes.size >= 32) { "app.security.jwt-secret must contain at least 32 bytes" }
		return SecretKeySpec(secretBytes, "HmacSHA256")
	}

	@Bean
	fun jwtDecoder(secretKey: SecretKey, properties: SecurityProperties): JwtDecoder {
		val decoder = NimbusJwtDecoder.withSecretKey(secretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build()
		decoder.setJwtValidator(
			DelegatingOAuth2TokenValidator(
				JwtValidators.createDefaultWithIssuer(properties.jwtIssuer),
				CustomerIdClaimValidator(),
			),
		)
		return decoder
	}
}
