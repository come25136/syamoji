package id.come25136.syamoji

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val bufferingMs: Int = 2000
        val loadControl: LoadControl = DefaultLoadControl.Builder().setBufferDurationsMs(
            bufferingMs + 500, // 最小バッファサイズ（ミリ秒）
            5000, // 最大バッファサイズ（ミリ秒）
            bufferingMs, // 再生のためのバッファサイズ（ミリ秒）
            bufferingMs // 再バッファリング後の再生のためのバッファサイズ
        ).build()
        player = ExoPlayer.Builder(this).setLoadControl(loadControl).build()

        val mediaSessionCallback = object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>
            ) = Futures.immediateFuture(mediaItems.map { mediaItem ->
                mediaItem.buildUpon().setUri(mediaItem.requestMetadata.mediaUri).build()
            })
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(mediaSessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        return mediaSession
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        mediaSession.release()
    }
}