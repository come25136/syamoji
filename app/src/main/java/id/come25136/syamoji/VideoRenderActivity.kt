package id.come25136.syamoji

import android.content.ComponentName
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@UnstableApi
class VideoRenderActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private lateinit var spinner: ProgressBar
    private lateinit var streamUrl: String
    private lateinit var mediaController: MediaController

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

        // テスト用に定期的にコメントを追加
        commentAdderJob.launch {
            while (!commentRender.isInitialized()){
                delay(100L)
            }

            while (isActive) {
                delay(25L)
                val autoComment = Comment(
                    text = "自動コメント ${System.currentTimeMillis()}",
                    color = Color.WHITE,
//                    size = 60f,
                    velocity = 10f
                )
                commentRender.addComment(autoComment)
            }
        }

        streamUrl = intent.getStringExtra("streamUrl")!!



        playerView = findViewById(R.id.player_view)
        spinner = findViewById(R.id.progressBar)

        // ExoPlayerの初期化
//        player = ExoPlayer.Builder(this).setLoadControl(loadControl).build()

        val component = ComponentName(this, PlaybackService::class.java)
        val token = SessionToken(this, component)

        val controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()

                // Call controllerFuture.get() to retrieve the MediaController.
                // MediaController implements the Player interface, so it can be
                // attached to the PlayerView UI component.
                playerView.player = mediaController

                val mediaItem = MediaItem
                    .Builder()
                    .setRequestMetadata(
                        MediaItem.RequestMetadata.Builder()
                            .setMediaUri(Uri.parse(intent.getStringExtra("streamUrl")!!))
                            .build()
                    ).build()
//                val mediaItem = MediaItem.fromUri(intent.getStringExtra("streamUrl")!!)

                mediaController.setMediaItem(mediaItem)

                //                playerView.player = player

                // メディアアイテムを設定
                // val mediaItem = MediaItem.fromUri("http://distribution.bbb3d.renderfarming.net/video/mp4/bbb_sunflower_1080p_60fps_normal.mp4")
//        val mediaItem = MediaItem.fromUri("http://192.168.20.10:40772/api/channels/GR/23/services/1072/stream") // テレ東
//        val mediaItem = MediaItem.fromUri("http://192.168.20.10:40772/api/channels/GR/21/services/1056/stream") // フジテレビ
//        val mediaItem = MediaItem.fromUri("http://192.168.20.10:40772/api/channels/GR/16/services/23608/stream") // TOKYO MX1
//        val mediaItem = MediaItem.fromUri("http://192.168.20.10:40772/api/channels/BS/BS09_0/services/211/stream") // BS11
//                val url = intent.getStringExtra("streamUrl")!!
//                Log.d("ExoPlayer", "Generated stream url: $url")
//                val mediaItem2 =
//                    MediaItem.fromUri(url)
//                player.setMediaItem(mediaItem2)

                var lastRetryDatetime = System.currentTimeMillis()

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

                                val now = System.currentTimeMillis()
//                                if (lastRetryDatetime + 1000 * 2 < now) {
                                // Coroutineを使って非同期で5秒の遅延を実行
                                CoroutineScope(Dispatchers.Main).launch {
                                    if (!(mediaController.isLoading || mediaController.isPlaying)) {
                                        Log.d("ExoPlayer", "リトライを開始します")

                                        delay(1000L) // 5秒遅延
                                        retryPlayback()
                                    }
                                }
//                                }
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
    }

    private fun retryPlayback() {
        Log.d("ExoPlayer", "再試行: プレイヤーを再準備します。")
        mediaController.prepare()
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaController.release()

        commentAdderJob.cancel()
    }
}