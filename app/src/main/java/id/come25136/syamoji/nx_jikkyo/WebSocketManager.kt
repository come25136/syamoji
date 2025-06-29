package id.come25136.syamoji.nx_jikkyo

import android.util.Log

interface WebSocketListener {
    fun onMessageReceived(message: String)
}

data class MessageServer(
    /**
     * example: niwavided
     */
    val type: String,

    /**
     * example: wss://nx-jikkyo.tsukumijima.net/api/v1/channels/jk6/ws/comment
     */
    val uri: String,
)

class WebSocketManager(private val channelId: String, private val listener: WebSocketListener) {
    private lateinit var watchSession: WatchSession
    private lateinit var commentSession: CommentSession

    fun connect() {
        val jkId = getJkIdFromChannelId(channelId)

        watchSession = WatchSession(jkId, object : WatchSessionListener {
            override fun onReady(watchSessionData: WatchSessionData) {
                Log.d("WebSocketManager", "Watch session is ready: $watchSessionData")

                commentSession =
                    CommentSession(jkId, watchSessionData, object : CommentSessionListener {
                        override fun onMessageReceived(message: String) {
                            listener.onMessageReceived(message)
                        }
                    })
            }
        })
    }

    fun close() {
        watchSession.close()
        commentSession.close()
    }
}
