package ai.pocket.spotclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;

/**
 * One countdown timer and one alarm, set by voice through Spot Voice (ai.pocket.heyclaude).
 * The broadcasts must name the package (-p), Android 8+ drops implicit ones for manifest receivers:
 *   am broadcast -a ai.pocket.spotclock.TIMER -p ai.pocket.spotclock --el seconds 600                (0 = cancel)
 *   am broadcast -a ai.pocket.spotclock.ALARM -p ai.pocket.spotclock --ei hour 6 --ei minute 30      (hour -1 = cancel)
 *   am broadcast -a ai.pocket.spotclock.STOP_RINGING -p ai.pocket.spotclock
 * Fires through AlarmManager, so it also works while the clock is not on screen.
 */
public class Timers extends BroadcastReceiver {
    static final String ACTION_TIMER = "ai.pocket.spotclock.TIMER";
    static final String ACTION_ALARM = "ai.pocket.spotclock.ALARM";
    static final String ACTION_STOP = "ai.pocket.spotclock.STOP_RINGING";
    static final String ACTION_FIRE = "ai.pocket.spotclock.FIRE";

    private static SharedPreferences prefs(Context c) {
        return Weather.prefs(c);
    }

    static long timerEnd(Context c) {
        long end = prefs(c).getLong("timer_end", 0);
        return end > System.currentTimeMillis() ? end : 0;
    }

    static long timerTotal(Context c) {
        return prefs(c).getLong("timer_total", 0);
    }

    static long alarmAt(Context c) {
        long at = prefs(c).getLong("alarm_at", 0);
        return at > System.currentTimeMillis() ? at : 0;
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (ACTION_TIMER.equals(a)) {
            setTimer(ctx, intent.getLongExtra("seconds", 0));
        } else if (ACTION_ALARM.equals(a)) {
            setAlarm(ctx, intent.getIntExtra("hour", -1), intent.getIntExtra("minute", 0));
        } else if (ACTION_STOP.equals(a)) {
            RingService.stop(ctx);
        } else if (ACTION_FIRE.equals(a)) {
            String kind = intent.getStringExtra("kind");
            if ("timer".equals(kind)) prefs(ctx).edit().remove("timer_end").apply();
            else prefs(ctx).edit().remove("alarm_at").apply();
            RingService.start(ctx, kind);
        }
    }

    static void setTimer(Context ctx, long seconds) {
        if (seconds <= 0) {
            prefs(ctx).edit().remove("timer_end").remove("timer_total").apply();
            cancel(ctx, "timer");
            Log.i("SpotClock", "timer cancelled");
            return;
        }
        long end = System.currentTimeMillis() + seconds * 1000L;
        prefs(ctx).edit().putLong("timer_end", end).putLong("timer_total", seconds * 1000L).apply();
        schedule(ctx, "timer", end);
        Log.i("SpotClock", "timer set: " + seconds + " s");
    }

    static void setAlarm(Context ctx, int hour, int minute) {
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            prefs(ctx).edit().remove("alarm_at").apply();
            cancel(ctx, "alarm");
            Log.i("SpotClock", "alarm cancelled");
            return;
        }
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
        prefs(ctx).edit().putLong("alarm_at", c.getTimeInMillis()).apply();
        schedule(ctx, "alarm", c.getTimeInMillis());
        Log.i("SpotClock", "alarm set: " + hour + ":" + minute);
    }

    private static PendingIntent fireIntent(Context ctx, String kind) {
        Intent i = new Intent(ctx, Timers.class).setAction(ACTION_FIRE).putExtra("kind", kind);
        return PendingIntent.getBroadcast(ctx, "timer".equals(kind) ? 1 : 2, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context ctx, String kind, long at) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fireIntent(ctx, kind));
    }

    private static void cancel(Context ctx, String kind) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(fireIntent(ctx, kind));
    }
}
