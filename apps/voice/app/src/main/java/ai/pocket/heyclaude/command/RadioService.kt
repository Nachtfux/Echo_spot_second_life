package ai.pocket.heyclaude.command

import ai.pocket.heyclaude.Constants
import ai.pocket.heyclaude.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * Internet radio player ("Hey Claude, spiel SWR3"). Exposes a MediaSession, so media keys
 * (Pause/weiter) work like for Spotify and the Spot clock shows station name and logo.
 */
class RadioService : Service() {
    companion object {
        private const val CHANNEL = "radio"
        private const val NOTIFICATION_ID = 21
        const val ACTION_PLAY = "ai.pocket.heyclaude.radio.PLAY"
        const val ACTION_STOP = "ai.pocket.heyclaude.radio.STOP"

        fun play(ctx: Context, station: RadioBrowser.Station) {
            val i = Intent(ctx, RadioService::class.java).setAction(ACTION_PLAY)
                .putExtra("name", station.name).putExtra("url", station.url).putExtra("favicon", station.favicon)
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, RadioService::class.java).setAction(ACTION_STOP))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private lateinit var session: MediaSession
    private var focus: AudioFocusRequest? = null
    private var stationName = "Radio"
    private var url: String? = null
    private var logo: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "HeyClaudeRadio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = resume()
                override fun onPause() = pause()
                override fun onStop() = stopSelf()
            })
            isActive = true
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Radio", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> {
                stationName = intent.getStringExtra("name") ?: "Radio"
                url = intent.getStringExtra("url")
                logo = null
                startStream()
                intent.getStringExtra("favicon")?.takeIf { it.startsWith("http") }?.let { loadLogo(it) }
            }
        }
        return START_NOT_STICKY
    }

    private fun startStream() {
        val u = url ?: return
        releasePlayer()
        if (!requestFocus()) Log.w(Constants.TAG, "Radio: no audio focus")
        setState(PlaybackState.STATE_BUFFERING)
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setOnPreparedListener {
                it.start()
                setState(PlaybackState.STATE_PLAYING)
                Log.i(Constants.TAG, "Radio playing: $stationName")
            }
            setOnErrorListener { _, what, extra ->
                Log.w(Constants.TAG, "Radio error $what/$extra for $u")
                VoiceCommands.tone(android.media.ToneGenerator.TONE_PROP_NACK)
                stopSelf()
                true
            }
            try {
                setDataSource(u)
                prepareAsync()
            } catch (e: Exception) {
                Log.w(Constants.TAG, "Radio: cannot open $u", e)
                stopSelf()
            }
        }
        updateMetadata()
    }

    private fun resume() {
        val p = player
        if (p == null) {
            startStream()
            return
        }
        // Live stream: reconnect instead of resuming a stale buffer.
        startStream()
    }

    private fun pause() {
        releasePlayer()
        setState(PlaybackState.STATE_PAUSED)
    }

    private fun releasePlayer() {
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
    }

    private fun requestFocus(): Boolean {
        val am = getSystemService(AudioManager::class.java)
        // Reuse one request for the service's lifetime: a second, new request (switching stations)
        // would make Android send AUDIOFOCUS_LOSS to the first listener, which then paused the
        // station that had just been started.
        val req = focus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) main.post { pause() }
            }.build().also { focus = it }
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun setState(state: Int) {
        session.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_PLAY_PAUSE)
            .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
            .build())
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun updateMetadata() {
        val b = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, stationName)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "Radio")
        logo?.let { b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(b.build())
    }

    private fun loadLogo(link: String) {
        Thread({
            try {
                val con = URL(link).openConnection() as HttpURLConnection
                con.connectTimeout = 8000
                con.readTimeout = 8000
                con.setRequestProperty("User-Agent", "HeyClaudeSpot/1.0")
                val bmp = con.inputStream.use { BitmapFactory.decodeStream(it) }
                con.disconnect()
                if (bmp != null && bmp.width >= 64) main.post {
                    logo = bmp
                    updateMetadata()
                }
            } catch (e: Exception) {
                Log.i(Constants.TAG, "Radio logo not loaded: $e")
            }
        }, "radio-logo").start()
    }

    private fun notification(): Notification =
        Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_spark)
            .setContentTitle(stationName)
            .setContentText("Radio")
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .build()

    override fun onDestroy() {
        releasePlayer()
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        session.isActive = false
        session.release()
        super.onDestroy()
    }
}
