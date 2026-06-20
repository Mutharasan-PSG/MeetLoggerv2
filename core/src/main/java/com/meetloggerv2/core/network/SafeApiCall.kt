package com.meetloggerv2.core.network

import retrofit2.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.google.gson.Gson
import com.google.gson.JsonObject

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
            val errorString = response.errorBody()?.string()
            val parsedErrorMessage = parseErrorJson(errorString) ?: "Server error: ${response.code()} ${response.message()}"
            return NetworkResult.Error(parsedErrorMessage)
        } catch (e: Exception) {
            return when (e) {
                is SocketTimeoutException -> NetworkResult.Error("Timeout - check your internet")
                is UnknownHostException -> NetworkResult.Error("Check your internet connection")
                is ConnectException -> NetworkResult.Error("Server is unreachable")
                else -> NetworkResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    private fun parseErrorJson(errorJsonString: String?): String? {
        if (errorJsonString.isNullOrEmpty()) return null
        return try {
            val jsonObject = Gson().fromJson(errorJsonString, JsonObject::class.java)

            // Scenario 1: New standardized error format {"success": false, "error": {"message": "..."}}
            if (jsonObject.has("error") && jsonObject.get("error").isJsonObject) {
                val errorObj = jsonObject.getAsJsonObject("error")
                if (errorObj.has("message") && !errorObj.get("message").isJsonNull) {
                    return errorObj.get("message").asString
                }
            }

            // Scenario 2: Legacy error format {"error": "..."}
            if (jsonObject.has("error") && jsonObject.get("error").isJsonPrimitive) {
                return jsonObject.get("error").asString
            }

            // Scenario 3: General API message format {"message": "..."}
            if (jsonObject.has("message") && jsonObject.get("message").isJsonPrimitive) {
                return jsonObject.get("message").asString
            }

            null
        } catch (e: Exception) {
            null
        }
    }
}

