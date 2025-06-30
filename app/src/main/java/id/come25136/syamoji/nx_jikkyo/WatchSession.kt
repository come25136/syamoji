package id.come25136.syamoji.nx_jikkyo

import android.util.Log
import id.come25136.syamoji.util.RequestUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import org.json.JSONObject
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

data class WatchSessionData(
    val messageServerUrl: String,
    val threadId: String,
    val yourPostKey: String,
    val vposBaseTime: ZonedDateTime,
)

interface WatchSessionListener {
    fun onReady(watchSessionData: WatchSessionData)
}

class WatchSession(jkId: String, private val listener: WatchSessionListener) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private fun sessionUrlBuilder(jkId: String): Request {
        return RequestUtil.requestBuilder("wss://nx-jikkyo.tsukumijima.net/api/v1/channels/${jkId}/ws/watch")
    }

    private lateinit var watchSocket: WebSocket

    private fun sendMessage(message: String) {
        Log.d(WatchSession::class.simpleName, "⬆️ $message")
        watchSocket.send(message)
    }

    init {
        init(jkId)
    }

    private fun init(jkId: String) {
        val request = sessionUrlBuilder(jkId)

        Log.d(this::class.simpleName, "Connecting to WebSocket: ${request.url}")
        watchSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(WatchSession::class.simpleName, "WebSocket opened: ${response.message}")

                sendMessage("{\"type\":\"startWatching\",\"data\":{\"reconnect\":false}}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(WatchSession::class.simpleName, "⬇️ $text")

                val json = JSONObject(text)

                val messageType = json.getString("type")
                if (messageType == "room") {
                    /**
                     * {
                     *   "type": "room",
                     *   "data": {
                     *     "messageServer": {
                     *       "uri": "wss://nx-jikkyo.tsukumijima.net/api/v1/channels/jk4/ws/comment",
                     *       "type": "niwavided"
                     *     },
                     *     "name": "アリーナ",
                     *     "threadId": "12018",
                     *     "isFirst": true,
                     *     "waybackkey": "DUMMY_TOKEN",
                     *     "yourPostKey": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                     *     "vposBaseTime": "2025-06-29T04:00:00+09:00"
                     *   }
                     * }
                     */

                    val payload = json.getJSONObject("data")
                    val messageServer = payload.getJSONObject("messageServer")

                    val type = messageServer.getString("type")
                    if (type == "niwavided") {
                        listener.onReady(
                            WatchSessionData(
                                messageServerUrl = messageServer.getString("uri"),
                                threadId = payload.getString("threadId"),
                                yourPostKey = payload.getString("yourPostKey"),
                                vposBaseTime = ZonedDateTime.parse(payload.getString("vposBaseTime"))
                            )
                        )

                        return
                    }

                    Log.e(
                        WatchSession::class.simpleName,
                        "Unsupported message server type: $type"
                    )

                    watchSocket.close(1000, "Unsupported message server type.")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(WatchSession::class.simpleName, "WebSocket closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d(WatchSession::class.simpleName, "WebSocket error: ${t.message}")
            }
        })
    }

    fun close() {
        watchSocket.close(1000, "close")
    }
}
