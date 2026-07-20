package com.fiap.hackathon.videomanagerapi.application.video

import java.util.UUID

fun interface AuthenticatedCustomerProvider {
	fun customerId(): UUID
}
