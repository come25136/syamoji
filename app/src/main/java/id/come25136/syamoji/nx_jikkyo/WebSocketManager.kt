package id.come25136.syamoji.nx_jikkyo

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface WebSocketListener {
    fun onMessageReceived(message: JSONObject)
}

class WebSocketManager(private val url: String, private val listener: WebSocketListener) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private val request: Request = Request.Builder()
        .url(url)
        .build()

    private lateinit var webSocket: WebSocket

    init {
        connect()
    }

    private fun connect() {
        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketManager", "WebSocket opened: ${response.message}")

                sendMessage("[{\"ping\":{\"content\":\"rs:0\"}},{\"ping\":{\"content\":\"ps:0\"}},{\"thread\":{\"version\":\"20061206\",\"thread\":\"4225\",\"threadkey\":\"5035c54c4b5002b5b8eed1fa13e0b42c41a0f52f\",\"user_id\":\"\",\"res_from\":-100}},{\"ping\":{\"content\":\"pf:0\"}},{\"ping\":{\"content\":\"rf:0\"}}]")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocketManager", "Message received: $text")

                val json = JSONObject(text)

                listener.onMessageReceived(json) // コールバックを呼び出す
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketManager", "WebSocket closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d("WebSocketManager", "WebSocket error: ${t.message}")
            }
        })
    }

    fun sendMessage(message: String) {
        Log.d("WebSocketManager","⬆️ $message")
        webSocket.send(message)
    }

    fun close() {
        webSocket.close(1000, "Client closed")
    }
}
