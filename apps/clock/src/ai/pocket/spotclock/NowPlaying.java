package ai.pocket.spotclock;

import android.graphics.Bitmap;
import android.os.SystemClock;

/** What is playing right now (any app with a media session: Spotify, the Hey Claude radio, …). */
final class NowPlaying {
    static volatile String title;
    static volatile String artist;
    static volatile Bitmap art;
    static volatile boolean playing;
    static volatile long position;       // ms at positionAt
    static volatile long positionAt;     // elapsedRealtime of the position sample
    static volatile long duration;       // ms, 0 = unknown / live stream
    static volatile float speed = 1f;
    static volatile long lastPlayingAt;  // elapsedRealtime

    /** Keep the cover on screen a little after a pause, so "Pause" does not flip the face instantly. */
    private static final long LINGER_MS = 20_000;

    private NowPlaying() {}

    static boolean show() {
        return title != null && (playing || SystemClock.elapsedRealtime() - lastPlayingAt < LINGER_MS);
    }

    static long currentPosition() {
        if (!playing) return position;
        return position + (long) ((SystemClock.elapsedRealtime() - positionAt) * speed);
    }

    static void clear() {
        title = null;
        artist = null;
        art = null;
        playing = false;
        duration = 0;
    }
}
