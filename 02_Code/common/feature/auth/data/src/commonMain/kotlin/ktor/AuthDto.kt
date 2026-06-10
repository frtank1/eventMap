package com.eventmap.auth.data.ktor

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RegisterRequest(val email: String, val password: String, val name: String)

@Serializable
data class ForgotRequest(val email: String)

@Serializable
data class ResetRequest(val email: String, val code: String, val newPassword: String)

@Serializable
data class InterestsRequest(val interests: List<String>)
