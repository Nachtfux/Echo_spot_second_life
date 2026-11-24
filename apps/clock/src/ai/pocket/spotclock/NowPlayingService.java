package ai.pocket.spotclock;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.util.List;

/**
 * Notification-listener access is what Android requires to read other apps' media sessions.
 * We only use it for that: title, artist, cover and position of whatever is playing.
 * Notifications themselves are ignored.
 */
public class NowPlayingService extends NotificationListenerService
        implements MediaSessionManager.OnActiveSessionsChangedListener {

    private MediaSessionManager sessions;
    private MediaController current;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            update();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            // Another session may have started playing; re-pick on every state change.
            if (sessions != null) onActiveSessionsChanged(sessions.getActiveSessions(component()));
            else update();
        }

        @Override
        public void onSessionDestroyed() {
            if (sessions != null) onActiveSessionsChanged(sessions.getActiveSessions(component()));
        }
    };

    private ComponentName component() {
        return new ComponentName(this, NowPlayingService.class);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sessions = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        try {
            sessions.addOnActiveSessionsChangedListener(this, component());
            onActiveSessionsChanged(sessions.getActiveSessions(component()));
            Log.i("SpotClock", "now-playing listener connected");
        } catch (SecurityException e) {
            Log.w("SpotClock", "no media session access", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(this);
        attach(null);
        super.onListenerDisconnected();
    }

    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        MediaController pick = null;
        if (controllers != null) {
            for (MediaController c : controllers) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    pick = c;
                    break;
                }
            }
            if (pick == null && current != null) {
                for (MediaController c : controllers) {
                    if (c.getSessionToken().equals(current.getSessionToken())) pick = c;
                }
            }
            if (pick == null && !controllers.isEmpty()) pick = controllers.get(0);
        }
        attach(pick);
    }

    private void attach(MediaController c) {
        if (current != null && (c == null || !current.getSessionToken().equals(c.getSessionToken()))) {
            current.unregisterCallback(callback);
            current = null;
        }
        if (c != null && current == null) {
            current = c;
            current.registerCallback(callback);
        }
        update();
    }

    private void update() {
        MediaController c = current;
        if (c == null) {
            NowPlaying.clear();
            return;
        }
        MediaMetadata m = c.getMetadata();
        PlaybackState s = c.getPlaybackState();
        String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (title == null && m != null) title = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        if (title == null || title.isEmpty()) {
            NowPlaying.clear();
            return;
        }
        String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist == null) artist = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);

        NowPlaying.title = title;
        NowPlaying.artist = artist;
        NowPlaying.art = art;
        NowPlaying.duration = Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION));
        boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        NowPlaying.playing = playing;
        if (playing) NowPlaying.lastPlayingAt = SystemClock.elapsedRealtime();
        if (s != null) {
            NowPlaying.position = Math.max(0, s.getPosition());
            NowPlaying.positionAt = s.getLastPositionUpdateTime() > 0 ? s.getLastPositionUpdateTime() : SystemClock.elapsedRealtime();
            NowPlaying.speed = s.getPlaybackSpeed() > 0 ? s.getPlaybackSpeed() : 1f;
        }
    }
}
