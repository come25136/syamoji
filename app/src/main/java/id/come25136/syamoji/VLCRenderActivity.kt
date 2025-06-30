package id.come25136.syamoji

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import id.come25136.syamoji.nx_jikkyo.CommentSocketListener
import id.come25136.syamoji.nx_jikkyo.CommentSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

@UnstableApi
class VLCRenderActivity : AppCompatActivity(), CommentSocketListener {
    private lateinit var playerView: VLCVideoLayout
    private lateinit var spinner: ProgressBar
    private lateinit var streamUrl: String
    private lateinit var commentSocketManager: CommentSocketManager

    private lateinit var commentRender: CommentRender
    private val commentAdderJob = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

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


        // URLの取得
        streamUrl = intent.getStringExtra("streamUrl")!!

        val options = listOf(
            "--network-caching=100",
        )

        // VLCのインスタンスを作成
        libVLC = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVLC)

        // SurfaceViewに関連付け
        mediaPlayer.attachViews(playerView, null, false, false)

        // メディアの設定
        prepareMediaPlayer()

        // WebSocket接続のセットアップ
        val channelId = intent.getStringExtra("channelId") ?: throw Error("No defined serviceId")
        commentSocketManager = CommentSocketManager(channelId, this)
        commentSocketManager.connect()

        // スクリーンをオンに保持
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        spinner.isVisible = false

        commentAdderJob.launch {
            while (true) {
                if (mediaPlayer.isReleased) {
                    cancel()

                    return@launch
                }

                delay(500)
                val vlcTimestamp = mediaPlayer.time // VLCの現在再生タイムスタンプ（ミリ秒）
//                commentRender.addComment(
//                    Comment(
//                        id = "1",
//                        timestamp = ZonedDateTime.now(),
//                        content = "わざとらしいのはちょっとな・・"
//                    )
//                )
//                commentRender.addComment(
//                    Comment(
//                        id = "1",
//                        timestamp = ZonedDateTime.now(),
//                        content = "草"
//                    )
//                )
//                commentRender.addComment(
//                    Comment(
//                        id = "1",
//                        timestamp = ZonedDateTime.now(),
//                        content = "止まるんじゃねぇぞ…"
//                    )
//                )

                Log.d("timestamp", "vlc timer: ${mediaPlayer.time}")
            }
        }
    }

    private fun prepareMediaPlayer() {
        // メディアの設定
        val media = Media(libVLC, Uri.parse(streamUrl))
        mediaPlayer.media = media
        media.release()  // メディアをセットした後にリリース

        // リスナーの設定
        mediaPlayer.setEventListener { event ->
//            Log.d("libVLC","Event: ${event.type.toString(16)}")
            when (event.type) {
                MediaPlayer.Event.Opening -> {
                    Log.d("libVLC", "オープン中...")
//                    spinner.isVisible = true
                }

                MediaPlayer.Event.Buffering -> {
                    mediaPlayer.isPlaying
                    Log.d("libVLC", "バッファリング中...")
//                    spinner.isVisible = true
                }

                MediaPlayer.Event.Vout -> {
                    Log.d("libVLC", "再生中")
//                    spinner.isVisible = false
                }

                MediaPlayer.Event.Stopped -> {
                    Log.d("libVLC", "再生停止")
                }

                MediaPlayer.Event.EndReached -> {
                    Log.d("libVLC", "再生終了")
                    retryPlayback()
                }

                MediaPlayer.Event.EncounteredError -> {
                    Log.e("libVLC", "再生エラーが発生")
                    retryPlayback()
                }
            }
        }

        // 再生の準備と開始
        retryPlayback()
    }

    private fun retryPlayback() {
        Log.d("libVLC", "再試行: プレイヤーを再準備します。")
        mediaPlayer.play()
    }

    override fun onDestroy() {
        super.onDestroy()

        // メディアプレイヤーとLibVLCのリソースを解放
        mediaPlayer.stop()
        mediaPlayer.detachViews()
        mediaPlayer.release()
        libVLC.release()

        commentAdderJob.cancel()

        commentSocketManager.close()

        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // CommentSocketListenerの実装
    override fun onReceiveReady() {
        TODO("Not yet implemented")
    }

    override fun onComment(comment: Comment) {
        if (comment.isPast) {
            Log.d(VLCRenderActivity::class.simpleName,"Ignore comment (isPast=true): [${comment.id}] ${comment.content}")
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            delay(100)
            commentRender.addComment(comment)
        }
    }
}
