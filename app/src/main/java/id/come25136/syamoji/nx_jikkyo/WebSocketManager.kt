package id.come25136.syamoji.nx_jikkyo

import android.util.Log
import id.come25136.syamoji.BuildConfig
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import java.util.concurrent.TimeUnit

interface WebSocketListener {
    fun onMessageReceived(message: String)
}

class WebSocketManager(private val channelId: String, private val listener: WebSocketListener) {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private val jkId = getJkIdFromChannelId(channelId)
    private val request: Request = Request.Builder()
        .header("user-agent", "syamoji/come25136_${BuildConfig.REVISION} (Android)")
        .url("wss://nx-jikkyo.tsukumijima.net/api/v1/channels/${jkId}/ws/comment")
        .build()

    private lateinit var webSocket: WebSocket

    fun connect() {
        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketManager", "WebSocket opened: ${response.message}")

                val jkId = getJkIdFromChannelId(channelId)

                fetchNxXml("https://nx-jikkyo.tsukumijima.net/api/v1/channels/xml").subscribeOn(
                    Schedulers.io()
                ).subscribe { nxData ->
                    val nxChannelInfo =
                        (nxData.channelList + nxData.bsChannelList)
                            .find { channel -> channel.video == jkId }
                            ?: throw Error("No supported channelId. channelId:${channelId}")

                    sendMessage("[{\"ping\":{\"content\":\"rs:0\"}},{\"ping\":{\"content\":\"ps:0\"}},{\"thread\":{\"version\":\"20061206\",\"thread\":\"${nxChannelInfo.thread.id}\",\"threadkey\":\"de0f5915bb7dde88051c224566fcdf6eb12c26a6\",\"user_id\":\"\",\"res_from\":-100}},{\"ping\":{\"content\":\"pf:0\"}},{\"ping\":{\"content\":\"rf:0\"}}]")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onMessageReceived(text) // コールバックを呼び出す
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
        Log.d("WebSocketManager", "⬆️ $message")
        webSocket.send(message)
    }

    fun close() {
        webSocket.close(1000, "Client closed")
    }
}
