package id.come25136.syamoji.nx_jikkyo

import android.util.Log
import id.come25136.syamoji.Comment
import id.come25136.syamoji.util.RequestUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

interface CommentSessionListener {
    fun onOpened()
    fun onComment(comment: Comment)
    fun onClosed()
}

class CommentSession(
    private val jkId: String,
    private val listener: CommentSessionListener
) : okhttp3.WebSocketListener() {
    private var closeNotify: Boolean = false

    private lateinit var watchSessionData: WatchSessionData
    private var lastNo = -1

    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private fun sessionUrlBuilder(jkId: String): Request {
        return RequestUtil.requestBuilder("wss://nx-jikkyo.tsukumijima.net/api/v1/channels/${jkId}/ws/comment")
    }

    private lateinit var commentSocket: WebSocket

    private fun sendMessage(message: String) {
        Log.d(CommentSession::class.simpleName, "⬆️ $message")
        commentSocket.send(message)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(CommentSession::class.simpleName, "WebSocket opened: ${response.message}")

        closeNotify = true

        sendMessage("[{\"ping\":{\"content\":\"rs:0\"}},{\"ping\":{\"content\":\"ps:0\"}},{\"thread\":{\"version\":\"20061206\",\"thread\":\"${watchSessionData.threadId}\",\"threadkey\":\"${watchSessionData.yourPostKey}\",\"user_id\":\"\",\"res_from\":0}},{\"ping\":{\"content\":\"pf:0\"}},{\"ping\":{\"content\":\"rf:0\"}}]")

        listener.onOpened()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(CommentSession::class.simpleName, "⬇️ $text")

        val json = JSONObject(text)

        if (json.has("chat")) {
            val chat = json.getJSONObject("chat")
            val isPast = chat.getInt("no") <= lastNo

            listener.onComment(
                Comment(
                    id = chat.getString("no"),
                    timestamp = Instant.ofEpochMilli(
                        chat.getLong("date") * 1000 + chat.getLong(
                            "date_usec"
                        ) / 1000
                    )
                        .atZone(ZoneId.of("Asia/Tokyo")),
                    isPast = isPast,
                    content = chat.getString("content")
                )
            )
        } else if (json.has("thread")) {
            val thread = json.getJSONObject("thread")
            lastNo = thread.getInt("last_res")
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(CommentSession::class.simpleName, "WebSocket closed: $reason")

        if (!closeNotify) listener.onClosed()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.d(CommentSession::class.simpleName, "WebSocket error: ${t.message}")

        if (!closeNotify) listener.onClosed()
    }

    private fun init(jkId: String, watchSessionData: WatchSessionData) {
        this.watchSessionData = watchSessionData
        val request = sessionUrlBuilder(jkId)

        Log.d(this::class.simpleName, "Connecting to WebSocket: ${request.url}")
        commentSocket =
            client.newWebSocket(request, this)
    }

    fun connect(watchSessionData: WatchSessionData) {
        init(jkId, watchSessionData)
    }

    fun close() {
        closeNotify = false

        commentSocket.close(1000, "close")
        commentSocket.cancel()
    }
}
