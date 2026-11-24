package ai.pocket.spotclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

/** Rings when a timer or alarm fires: alarm sound in a loop, max. 2 minutes, until stopped. */
public class RingService extends Service {
    static volatile String ringing;   // "timer" | "alarm" | null
    private static final long MAX_RING_MS = 120_000;
    private static final String CHANNEL = "ring";

    private MediaPlayer player;
    private PowerManager.WakeLock wake;
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void start(Context ctx, String kind) {
        ctx.startForegroundService(new Intent(ctx, RingService.class).putExtra("kind", kind));
    }

    static void stop(Context ctx) {
        if (ringing != null) ctx.stopService(new Intent(ctx, RingService.class));
        ringing = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String kind = intent != null && intent.getStringExtra("kind") != null ? intent.getStringExtra("kind") : "timer";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Timer & Wecker", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("timer".equals(kind) ? "Timer abgelaufen" : "Wecker")
                .setContentText("Tippen auf die Uhr oder „Hey Claude, stopp“")
                .build();
        startForeground(7, n);
        ringing = kind;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        //noinspection deprecation
        wake = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "SpotClock:ring");
        wake.acquire(MAX_RING_MS);

        if (player == null) {
            try {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                player.setDataSource(this, uri);
                player.setLooping(true);
                player.prepare();
                player.start();
            } catch (Exception e) {
                Log.w("SpotClock", "alarm sound failed", e);
            }
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopSelf();
            }
        }, MAX_RING_MS);
        // Bring the clock to the front so the ringing face (tap to stop) is visible.
        try {
            startActivity(new Intent(this, ClockActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w("SpotClock", "cannot show clock", e);
        }
        Log.i("SpotClock", "ringing: " + kind);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (wake != null && wake.isHeld()) wake.release();
        ringing = null;
        Log.i("SpotClock", "ringing stopped");
        super.onDestroy();
    }
}
