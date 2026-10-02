package com.pip.shared.network.http

enum class HttpMethodKind { GET, POST, PUT, PATCH, DELETE }

/**
 * Mirrors iOS's `APIEndpoint` protocol, scoped to the 3 REST endpoints that
 * actually exist today (PRD §4.4) — no auth/refresh endpoints, since none
 * exist server-side either.
 */
sealed class ApiEndpoint(val path: String, val method: HttpMethodKind) {
    class History(sessionId: String) :
        ApiEndpoint("/api/session/$sessionId/history", HttpMethodKind.GET)

    class SetListeningMode(sessionId: String, mode: String) :
        ApiEndpoint("/api/session/$sessionId/listen?mode=$mode", HttpMethodKind.PUT)

    class SetVoiceMode(sessionId: String, mode: String) :
        ApiEndpoint("/api/session/$sessionId/voice_mode?mode=$mode", HttpMethodKind.PUT)

    data object Login : ApiEndpoint("/auth/login", HttpMethodKind.POST)

    data object Signup : ApiEndpoint("/auth/signup", HttpMethodKind.POST)

    data object GetProfile : ApiEndpoint("/profile", HttpMethodKind.GET)

    data object UpdateProfile : ApiEndpoint("/profile", HttpMethodKind.PATCH)

    data object ResetProfile : ApiEndpoint("/profile/reset", HttpMethodKind.POST)

    data object DeleteProfile : ApiEndpoint("/profile", HttpMethodKind.DELETE)

    class GetProfileHistory(limit: Int = 50) : ApiEndpoint("/profile/history?limit=$limit", HttpMethodKind.GET)

    /** The token's own account identity — re-fetchable after relaunch, unlike
     * [Login]/[Signup], which only return it inline on that one call. */
    data object GetMe : ApiEndpoint("/auth/me", HttpMethodKind.GET)

    data object GetSettings : ApiEndpoint("/settings", HttpMethodKind.GET)

    data object UpdateSettings : ApiEndpoint("/settings", HttpMethodKind.PATCH)
}
