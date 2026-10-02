package com.pip.shared.network.http

import com.pip.shared.core.config.AppConfig
import com.pip.shared.core.result.PipError
import com.pip.shared.core.result.PipResult
import com.pip.shared.core.storage.KeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Thin REST wrapper — replaces iOS's Alamofire-backed `APIClient`, which is
 * iOS-only and can't live in `commonMain` (PRD §4.6/§5.3). Scoped to the
 * REST endpoints in [ApiEndpoint].
 *
 * Takes [KeyValueStore] directly (not `AuthRepository`) to attach the bearer
 * token, specifically to avoid a dependency cycle — `AuthRepository` itself
 * depends on this client to make the `/auth/login`/`/auth/signup` calls.
 * [AUTH_TOKEN_KEY] is the same key `AuthRepository` writes to.
 */
class KtorHttpClient(
    @PublishedApi internal val appConfig: AppConfig,
    @PublishedApi internal val keyValueStore: KeyValueStore,
) {
    @PublishedApi
    internal val client =
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            installNetworkDebugTools()
        }

    @PublishedApi
    internal fun ktorMethod(kind: HttpMethodKind): HttpMethod =
        when (kind) {
            HttpMethodKind.GET -> HttpMethod.Get
            HttpMethodKind.POST -> HttpMethod.Post
            HttpMethodKind.PUT -> HttpMethod.Put
            HttpMethodKind.PATCH -> HttpMethod.Patch
            HttpMethodKind.DELETE -> HttpMethod.Delete
        }

    suspend inline fun <reified T> request(endpoint: ApiEndpoint): PipResult<T> =
        try {
            val response =
                client.request(appConfig.apiBaseUrl + endpoint.path) {
                    method = ktorMethod(endpoint.method)
                    keyValueStore.getString(AUTH_TOKEN_KEY)?.let { header("Authorization", "Bearer $it") }
                }
            when {
                response.status.isSuccess() -> PipResult.Success(response.body())
                response.status == HttpStatusCode.Unauthorized -> PipResult.Failure(PipError.Unauthorized)
                else -> PipResult.Failure(PipError.Server(response.status.value, response.status.description))
            }
        } catch (e: Exception) {
            PipResult.Failure(PipError.NoConnectivity)
        }

    /** For endpoints that POST a JSON body and decode a JSON response (auth.py's `/auth/login`/`/auth/signup`) —
     * unlike [request], which never attaches a body (fine for the GET/PUT-with-query-param endpoints above). */
    suspend inline fun <reified TReq, reified TRes> requestWithBody(endpoint: ApiEndpoint, body: TReq): PipResult<TRes> =
        try {
            val response =
                client.request(appConfig.apiBaseUrl + endpoint.path) {
                    method = ktorMethod(endpoint.method)
                    keyValueStore.getString(AUTH_TOKEN_KEY)?.let { header("Authorization", "Bearer $it") }
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            when {
                response.status.isSuccess() -> PipResult.Success(response.body())
                response.status == HttpStatusCode.Unauthorized -> PipResult.Failure(PipError.Unauthorized)
                else -> PipResult.Failure(PipError.Server(response.status.value, response.status.description))
            }
        } catch (e: Exception) {
            PipResult.Failure(PipError.NoConnectivity)
        }

    /** For endpoints whose response body isn't meaningful to decode (PUT toggles today, PRD §4.4). */
    suspend fun requestNoBody(endpoint: ApiEndpoint): PipResult<Unit> =
        try {
            val response =
                client.request(appConfig.apiBaseUrl + endpoint.path) {
                    method = ktorMethod(endpoint.method)
                    keyValueStore.getString(AUTH_TOKEN_KEY)?.let { header("Authorization", "Bearer $it") }
                }
            when {
                response.status.isSuccess() -> PipResult.Success(Unit)
                response.status == HttpStatusCode.Unauthorized -> PipResult.Failure(PipError.Unauthorized)
                else -> PipResult.Failure(PipError.Server(response.status.value, response.status.description))
            }
        } catch (e: Exception) {
            PipResult.Failure(PipError.NoConnectivity)
        }

    companion object {
        /** Same key `AuthRepository` reads/writes — the token lives in one place (`KeyValueStore`), not duplicated. */
        const val AUTH_TOKEN_KEY = "auth.token"
    }
}
