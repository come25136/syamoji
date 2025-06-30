package id.come25136.syamoji.nx_jikkyo

import android.util.Log
import id.come25136.syamoji.Comment

interface CommentSocketListener {
    fun onReceiveReady()
    fun onComment(comment: Comment)
}

class CommentSocketManager(private val channelId: String, private val listener: CommentSocketListener) {
    private var watchSession: WatchSession? = null
    private var commentSession: CommentSession? = null

    fun connect() {
        val jkId = getJkIdFromChannelId(channelId)

        watchSession = WatchSession(jkId, object : WatchSessionListener {
            override fun onReady(watchSessionData: WatchSessionData) {
                Log.d("WebSocketManager", "Watch session is ready: $watchSessionData")

                commentSession =
                    CommentSession(jkId, watchSessionData, object : CommentSessionListener {
                        override fun onComment(comment: Comment) {
                            listener.onComment(comment)
                        }
                    })
            }
        })
    }

    fun close() {
        watchSession?.close()
        commentSession?.close()
    }
}
