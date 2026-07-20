package com.fiap.hackathon.videomanagerapi.application.video

import java.util.UUID

interface AuthenticatedCustomerProvider {
	fun customerId(): UUID
}
