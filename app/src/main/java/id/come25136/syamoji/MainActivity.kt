package id.come25136.syamoji

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.app.Activity
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.ui.PlayerView
import com.jakewharton.threetenabp.AndroidThreeTen

class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_2)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

        AndroidThreeTen.init(this)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EpgFragment())
            .commit()

        /*
        playerView = findViewById(R.id.player_view)

        val bufferingMs: Int = 2000

        val loadControl: LoadControl = DefaultLoadControl.Builder().setBufferDurationsMs(
            bufferingMs + 500, // 最小バッファサイズ（ミリ秒）
            5000, // 最大バッファサイズ（ミリ秒）
            bufferingMs, // 再生のためのバッファサイズ（ミリ秒）
            bufferingMs // 再バッファリング後の再生のためのバッファサイズ
        ).build()

        return

        // ExoPlayerの初期化
        player = ExoPlayer.Builder(this).setLoadControl(loadControl).build()
        playerView.player = player

        // メディアアイテムを設定
        // val mediaItem = MediaItem.fromUri("http://distribution.bbb3d.renderfarming.net/video/mp4/bbb_sunflower_1080p_60fps_normal.mp4")
        val mediaItem =
            MediaItem.fromUri("http://192.168.20.10:40772/api/channels/GR/23/services/1072/stream") // テレ東
//        val mediaItem = MediaItem.fromUri("http://192.168.20.10:40772/api/channels/GR/21/services/1056/stream") // フジテレビ
//        val mediaItem = MediaItem.fromUri("http://192.168.20.10:40772/api/channels/GR/16/services/23608/stream") // TOKYO MX1
//        val mediaItem = MediaItem.fromUri("http://192.168.20.10:40772/api/channels/BS/BS09_0/services/211/stream") // BS11
        player.setMediaItem(mediaItem)

        // リスナーの追加
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        Log.d("ExoPlayer", "バッファリング中...")
                        // バッファリング中のUI表示などをここに追加
                    }

                    Player.STATE_READY -> {
                        Log.d("ExoPlayer", "再生準備完了")
                        // 再生が準備完了したら自動再生を開始
                        if (!player.isPlaying) {
                            player.play()
                        }
                    }

                    Player.STATE_ENDED -> {
                        Log.d("ExoPlayer", "再生終了")
                        // 再生終了後の処理をここに追加
                    }

                    Player.STATE_IDLE -> {
                        Log.d("ExoPlayer", "プレイヤーがアイドル状態")
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
        player.prepare()
        player.play()
        */
    }

    private fun retryPlayback() {
        Log.d("ExoPlayer", "再試行: プレイヤーを再準備します。")
        player.seekTo(0)
        player.prepare()
        player.play()
    }

    override fun onStop() {
        super.onStop()
        player.release() // プレイヤーを解放
    }
}