package com.kornerz.core.network.videocall

import okhttp3.OkHttpClient

/**
 * Abstraction over OkHttp's [okhttp3.WebSocket] so [SessionTokenService]
 * can be unit-tested with a fake implementation. The real
 * implementation lives in [SessionTokenService] via the
 * [OkHttpWebSocketFactory].
 */
fun interface WebSocketFactory {
    fun create(
        client: OkHttpClient,
        request: okhttp3.Request,
        listener: okhttp3.WebSocketListener,
    ): okhttp3.WebSocket
}

internal object OkHttpWebSocketFactory : WebSocketFactory {
    override fun create(
        client: OkHttpClient,
        request: okhttp3.Request,
        listener: okhttp3.WebSocketListener,
    ): okhttp3.WebSocket = client.newWebSocket(request, listener)
}