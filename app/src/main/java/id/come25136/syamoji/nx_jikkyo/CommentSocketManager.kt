package id.come25136.syamoji.nx_jikkyo

import android.util.Log
import id.come25136.syamoji.Comment
import java.lang.Thread

interface CommentSocketListener {
    fun onReceiveReady()
    fun onComment(comment: Comment)
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class CommentSocketManager(
    channelId: String,
    private val listener: CommentSocketListener
) {
    private val watchSession: WatchSession
    private val commentSession: CommentSession

    private var connectionId: Int = -1
    private var connectionStatus: ConnectionState = ConnectionState.DISCONNECTED
    private var closeByUser: Boolean = false

    init {
        val jkId = getJkIdFromChannelId(channelId)

        commentSession =
            CommentSession(jkId, object : CommentSessionListener {
                override fun onOpened() {
                    connectionStatus = ConnectionState.CONNECTED
                }

                override fun onComment(comment: Comment) {
                    listener.onComment(comment)
                }

                override fun onClosed() {
                    if (connectionStatus == ConnectionState.CONNECTED) reconnect()
                }
            })

        watchSession = WatchSession(jkId, object : WatchSessionListener {
            override fun onReady(watchSessionData: WatchSessionData) {
                Log.d(
                    CommentSocketManager::class.simpleName,
                    "[$connectionId] Watch session is ready: $watchSessionData"
                )

                commentSession.connect(watchSessionData)
            }

            override fun onClosed() {
                reconnect()
            }
        })
    }

    fun connect() {
        connectionId += 1

        connectionStatus = ConnectionState.CONNECTING
        watchSession.connect()
    }

    private fun reconnect() {
        if (closeByUser) {
            Log.d(
                CommentSocketManager::class.simpleName,
                "[$connectionId] No auto reconnect because the user closed it."
            )

            return
        }

        Log.d(
            CommentSocketManager::class.simpleName,
            "[$connectionId] Reconnect process started..."
        )
        close(false)

        Log.d(
            CommentSocketManager::class.simpleName,
            "[$connectionId] Waiting for 3 seconds before reconnecting..."
        )
        Thread.sleep(3000)

        Log.d(CommentSocketManager::class.simpleName, "[$connectionId] Reconnecting now...")
        connect()
    }

    fun close(closeByUser: Boolean) {
        if (connectionStatus == ConnectionState.DISCONNECTED) {
            Log.d(CommentSocketManager::class.simpleName, "[$connectionId] Connection closed")

            return
        }

        this.closeByUser = closeByUser
        connectionStatus = ConnectionState.DISCONNECTED

        commentSession.close()
        watchSession.close()
    }
}
