package id.come25136.syamoji.nx_jikkyo

import android.util.Log
import id.come25136.syamoji.util.RequestUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import java.util.concurrent.TimeUnit

interface CommentSessionListener {
    fun onMessageReceived(message: String)
}

class CommentSession(
    jkId: String,
    watchSessionData: WatchSessionData,
    private val listener: CommentSessionListener
) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    fun sessionUrlBuilder(jkId: String): Request {
        return RequestUtil.requestBuilder("wss://nx-jikkyo.tsukumijima.net/api/v1/channels/${jkId}/ws/comment")
    }

    private lateinit var commentSocket: WebSocket

    fun sendMessage(message: String) {
        Log.d(WatchSession::class.simpleName, "⬆️ $message")
        commentSocket.send(message)
    }

    init {
        init(jkId, watchSessionData)
    }

    private fun init(jkId: String, watchSessionData: WatchSessionData) {
        val request = sessionUrlBuilder(jkId)

        Log.d(this::class.simpleName, "Connecting to WebSocket: ${request.url}")
        commentSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(WatchSession::class.simpleName, "WebSocket opened: ${response.message}")

                sendMessage("[{\"ping\":{\"content\":\"rs:0\"}},{\"ping\":{\"content\":\"ps:0\"}},{\"thread\":{\"version\":\"20061206\",\"thread\":\"${watchSessionData.threadId}\",\"threadkey\":\"${watchSessionData.yourPostKey}\",\"user_id\":\"\",\"res_from\":-100}},{\"ping\":{\"content\":\"pf:0\"}},{\"ping\":{\"content\":\"rf:0\"}}]")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(WatchSession::class.simpleName, "⬇️ $text")

                listener.onMessageReceived(text) // コールバックを呼び出す
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
        commentSocket.close(1000, "close")
    }
}
