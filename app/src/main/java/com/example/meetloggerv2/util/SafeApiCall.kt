package com.example.meetloggerv2.util

import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

interface SafeApiCall {
    suspend fun <T> safeApiCall(apiCall: suspend () -> Response<T>): NetworkResult<T> {
        try {
            val response = apiCall()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    return NetworkResult.Success(body)
                }
            }
            return NetworkResult.Error("Server error: ${response.code()} ${response.message()}")
        } catch (e: Exception) {
            return when (e) {
                is SocketTimeoutException -> NetworkResult.Error("Timeout - check your internet")
                is UnknownHostException -> NetworkResult.Error("Check your internet connection")
                is ConnectException -> NetworkResult.Error("Server is unreachable")
                else -> NetworkResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}
