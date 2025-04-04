package id.come25136.syamoji

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

        val loadControl: LoadControl = DefaultLoadControl.Builder().setBufferDurationsMs(
            50, // 最小バッファサイズ（ミリ秒）
            200, // 最大バッファサイズ（ミリ秒）
            50, // 再生のためのバッファサイズ（ミリ秒）
            50 // 再バッファリング後の再生のためのバッファサイズ
        ).build()
        player = ExoPlayer.Builder(this).setLoadControl(loadControl).build()

        val mediaSessionCallback = object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>
            ) = Futures.immediateFuture(mediaItems.map { mediaItem ->
                val metadata = MediaMetadata
                    .Builder()
                    .setTitle("aaa")
                    .setMediaType(MediaMetadata.MEDIA_TYPE_TV_CHANNEL)
                    // 必要な他のメタデータを設定
                    .build()
                mediaItem
                    .buildUpon()
                    .setUri(mediaItem.requestMetadata.mediaUri)
                    .setMediaMetadata(metadata)
                    .build()
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