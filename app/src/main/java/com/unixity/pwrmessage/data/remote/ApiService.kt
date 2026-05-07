package com.unixity.pwrmessage.data.remote

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

const val API_URL = "https://api.teampwr.dev"
const val WS_URL = "https://api.teampwr.dev"

// --- Request/Response models ---

data class AuthRequest(val user: String, val pass: String)
data class AuthResponse(val token: String, val user: String)
data class ErrorResponse(val error: String)

// --- API Interface ---

interface ApiService {

    @POST("api/login")
    suspend fun login(@Body body: AuthRequest): Response<AuthResponse>

    @POST("api/register")
    suspend fun register(@Body body: AuthRequest): Response<AuthResponse>

    @GET("api/user/{username}")
    suspend fun lookupUser(
        @Path("username") username: String,
        @Header("Authorization") token: String
    ): Response<Unit>

    companion object {
        @Volatile private var INSTANCE: ApiService? = null

        fun getInstance(): ApiService {
            return INSTANCE ?: Retrofit.Builder()
                .baseUrl("$API_URL/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
                .also { INSTANCE = it }
        }
    }
}