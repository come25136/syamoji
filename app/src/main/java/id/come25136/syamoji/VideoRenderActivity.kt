package id.come25136.syamoji

import android.content.ComponentName
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors
import id.come25136.syamoji.nx_jikkyo.WebSocketListener
import id.come25136.syamoji.nx_jikkyo.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@UnstableApi
class VideoRenderActivity : AppCompatActivity(), WebSocketListener {
    private lateinit var playerView: PlayerView
    private lateinit var spinner: ProgressBar
    private lateinit var streamUrl: String
    private lateinit var mediaController: MediaController
    private lateinit var webSocketManager: WebSocketManager

    private lateinit var commentRender: CommentRender
    private val commentAdderJob = CoroutineScope(Dispatchers.Default + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_video_render)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

        commentRender = findViewById(R.id.commentRender)


        streamUrl = intent.getStringExtra("streamUrl")!!



        playerView = findViewById(R.id.player_view)
        spinner = findViewById(R.id.progressBar)


        val component = ComponentName(this, PlaybackService::class.java)
        val token = SessionToken(this, component)

        val controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                playerView.player = mediaController

                val mediaItem = MediaItem
                    .Builder()
                    .setRequestMetadata(
                        MediaItem.RequestMetadata.Builder()
                            .setMediaUri(Uri.parse(intent.getStringExtra("streamUrl")!!))
                            .build()
                    ).build()

                mediaController.setMediaItem(mediaItem)

                // リスナーの追加
                mediaController.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                Log.d("ExoPlayer", "バッファリング中...")
                                // バッファリング中のUI表示などをここに追加
                                spinner.isVisible = true
                            }

                            Player.STATE_READY -> {
                                Log.d("ExoPlayer", "再生準備完了")
                                spinner.isVisible = false
                                // 再生が準備完了したら自動再生を開始
                                if (!mediaController.isPlaying) {
                                    mediaController.play()
                                }
                            }

                            Player.STATE_ENDED -> {
                                Log.d("ExoPlayer", "再生終了")
                                // 再生終了後の処理をここに追加
                            }

                            Player.STATE_IDLE -> {
                                Log.d("ExoPlayer", "プレイヤーがアイドル状態")

                                CoroutineScope(Dispatchers.Main).launch {
                                    if (!(mediaController.isLoading || mediaController.isPlaying)) {
                                        Log.d("ExoPlayer", "リトライを開始します")

                                        delay(100)
                                        retryPlayback()
                                    }
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("ExoPlayer", "エラー: ${error.message} コード： ${error.errorCode}")
                        if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
                            Log.d("ExoPlayer", "ソースエラー発生。再試行します。")
                            retryPlayback()
                        } else {
                            // 他のタイプのエラー処理をここに追加
                        }
                    }
                })

                // プレイヤーを準備
                retryPlayback()
            },
            MoreExecutors.directExecutor()
        )

        val channelId = intent.getStringExtra("channelId") ?: throw Error("No defined serviceId")
        webSocketManager =
            WebSocketManager(channelId, this)
        webSocketManager.connect()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun retryPlayback() {
        Log.d("ExoPlayer", "再試行: プレイヤーを再準備します。")
        mediaController.prepare()
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaController.release()

        commentAdderJob.cancel()

        webSocketManager.close()

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // WebSocketListenerの実装
    override fun onMessageReceived(message: JSONObject) {
        val chat = message.has("chat")
        if (!chat) return

        // UIに反映する処理などをここに追加
        while (!commentRender.isInitialized()) {
            Thread.sleep(100L)
        }

        val autoComment = Comment(
            text = message.getJSONObject("chat").getString("content"),
            color = Color.WHITE,
            velocity = 8f
        )
        CoroutineScope(Dispatchers.Default).launch {
            delay(100)
            commentRender.addComment(autoComment)
        }
    }
}