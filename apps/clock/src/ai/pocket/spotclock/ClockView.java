package ai.pocket.spotclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Full-screen analogue face for the round 480x480 Spot display, drawn in a
 * 480-unit coordinate space centred on the screen. Four complications (sun,
 * weather, rain, date + moon), Claude status in the centre cap, and a dimmed
 * night face from 22:00 to 07:00. Layout follows the "Vollbild mit
 * Komplikationen" design board.
 */
public class ClockView extends View {
    static final int IVORY = 0xFFFAF9F5;
    static final int DARK = 0xFF141413;
    static final int ACCENT = 0xFFD97757;
    static final int GRAY = 0xFFB0AEA5;

    /** Sent by Hey Claude: extra "state" = off | listening | active. */
    static final String ACTION_STATE = "ai.pocket.heyclaude.STATE";
    /** Sent by the clock on start so Hey Claude can answer with its state. */
    static final String ACTION_QUERY = "ai.pocket.heyclaude.QUERY_STATE";
    private static final String HEY_CLAUDE_PKG = "ai.pocket.heyclaude";

    /** Brightness follows the room light: [night] = dark face, [brightness] 0..1 or -1 = system setting. */
    interface NightListener {
        void onNightChanged(boolean night, float brightness);
    }

    // Room-light levels (lux bucket boundaries) and the screen brightness used for each.
    private static final float[] LUX_BOUNDS = {4f, 30f, 250f};
    private static final float[] BRIGHTNESS = {0.05f, 0.25f, 0.6f, -1f};
    private static final long BUCKET_SETTLE_MS = 4000;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap artSource;
    private Bitmap artScaled;
    private final Typeface serif = Typeface.create(Typeface.SERIF, Typeface.NORMAL);
    private final Typeface sans = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm", Locale.GERMAN);
    private final SimpleDateFormat weekdayFmt = new SimpleDateFormat("EE", Locale.GERMAN);
    private final SimpleDateFormat monthFmt = new SimpleDateFormat("MMM", Locale.GERMAN);
    private final SparseArray<Drawable> icons = new SparseArray<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();
    private final Path path = new Path();

    private String claudeState = "off";
    private boolean night;
    private NightListener nightListener;
    private SensorManager sensors;
    private Sensor lightSensor;
    private volatile float lux = -1f;
    private int lightBucket = -1;
    private int reportedBucket = -2;
    private int pendingBucket = -1;
    private long pendingSince;

    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent e) {
            float v = e.values[0];
            lux = lux < 0 ? v : lux * 0.6f + v * 0.4f;
            postInvalidate();   // react quickly even while the night face ticks only every 30 s
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private long lastWeatherCheck;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
            schedule();
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String s = intent.getStringExtra("state");
            if (s == null) return;
            claudeState = s;
            handler.removeCallbacks(tick);
            invalidate();
            schedule();
        }
    };

    public ClockView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        fill.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
    }

    void setNightListener(NightListener l) {
        nightListener = l;
        reportedBucket = -2;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Context c = getContext();
        c.registerReceiver(stateReceiver, new IntentFilter(ACTION_STATE));
        if (isInstalled(HEY_CLAUDE_PKG)) {
            c.sendBroadcast(new Intent(ACTION_QUERY).setPackage(HEY_CLAUDE_PKG));
        }
        Weather.load(c);
        lastWeatherCheck = 0;
        sensors = (SensorManager) c.getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (lightSensor != null) sensors.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        handler.removeCallbacks(tick);
        schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        if (sensors != null) sensors.unregisterListener(lightListener);
        try {
            getContext().unregisterReceiver(stateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDetachedFromWindow();
    }

    private void schedule() {
        long now = System.currentTimeMillis();
        long delay;
        if (RingService.ringing != null || "active".equals(claudeState)) delay = 33;   // pulse animation
        else if (night) delay = 30000 - (now % 30000);                // no second hand at night
        else delay = 1000 - (now % 1000);                             // tick on the second
        handler.postDelayed(tick, delay);
    }

    private boolean isInstalled(String pkg) {
        try {
            getContext().getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        updateLightBucket(now);
        boolean dark = lightSensor != null && lightBucket >= 0 ? lightBucket == 0 : (hour >= 22 || hour < 7);
        night = dark && RingService.ringing == null;
        int b = RingService.ringing != null ? 3 : (lightSensor != null && lightBucket >= 0 ? lightBucket : (night ? 0 : 3));
        if (b != reportedBucket) {
            reportedBucket = b;
            if (nightListener != null) nightListener.onNightChanged(night, BRIGHTNESS[b]);
        }
        if (now - lastWeatherCheck > 60000) {
            lastWeatherCheck = now;
            Weather.maybeFetch(getContext(), new Runnable() {
                @Override
                public void run() {
                    postInvalidate();
                }
            });
        }

        c.drawColor(night ? Color.BLACK : DARK);
        float scale = Math.min(getWidth(), getHeight()) / 480f;
        c.save();
        c.translate(getWidth() / 2f, getHeight() / 2f);
        c.scale(scale, scale);
        if (RingService.ringing != null) drawRinging(c, cal);
        else if (night) drawNight(c, cal);
        else if (NowPlaying.show()) drawNowPlaying(c, cal);
        else drawDay(c, cal, now);
        if (RingService.ringing == null) drawTimerAndAlarm(c, now);
        c.restore();
    }

    // ------------------------------------------------------------------ day --

    private void drawDay(Canvas c, Calendar cal, long now) {
        for (int i = 0; i < 60; i++) {
            c.save();
            c.rotate(i * 6f);
            if (i % 5 == 0) {
                fill.setColor(IVORY);
                rect.set(-2.5f, -231f, 2.5f, -209f);
                c.drawRoundRect(rect, 2.5f, 2.5f, fill);
            } else {
                fill.setColor(alpha(IVORY, 0.30f));
                rect.set(-1f, -231f, 1f, -221f);
                c.drawRoundRect(rect, 1f, 1f, fill);
            }
            c.restore();
        }

        Weather.Data w = Weather.data;
        drawSun(c, 0, -110, w, now);
        drawWeather(c, -110, 0, w);
        drawRain(c, 110, 0, w);
        drawDate(c, 0, 110, cal, now);

        int h = cal.get(Calendar.HOUR);
        int m = cal.get(Calendar.MINUTE);
        int s = cal.get(Calendar.SECOND);
        hand(c, (h + m / 60f) * 30f, 10f, 140f, 6f, IVORY, true);
        hand(c, (m + s / 60f) * 6f, 8f, 210f, 6f, IVORY, true);
        hand(c, s * 6f, 2f, 218f, 38f, ACCENT, false);
        drawCenter(c);
    }

    private void complication(Canvas c, float cx, float cy) {
        fill.setColor(alpha(IVORY, 0.05f));
        c.drawCircle(cx, cy, 68f, fill);
        stroke.setStrokeWidth(1f);
        stroke.setColor(alpha(IVORY, 0.15f));
        c.drawCircle(cx, cy, 67.5f, stroke);
    }

    private void drawSun(Canvas c, float cx, float cy, Weather.Data w, long now) {
        complication(c, cx, cy);
        icon(c, R.drawable.ic_sun_horizon, cx, cy - 26f, 32f);
        String value = "—";
        String label = "SONNE";
        if (w != null) {
            long t;
            if (now < w.sunrise) {
                t = w.sunrise;
                label = "AUFGANG";
            } else if (now < w.sunset) {
                t = w.sunset;
                label = "UNTERGANG";
            } else {
                t = w.sunriseTomorrow;
                label = "AUFGANG";
            }
            if (t > 0) value = hhmm.format(new Date(t));
        }
        label(c, value, cx, cy + 16f, 24f, sansMedium, IVORY, 0f);
        label(c, label, cx, cy + 36f, 10.5f, sansMedium, alpha(IVORY, 0.62f), 1.8f);
    }

    private void drawWeather(Canvas c, float cx, float cy, Weather.Data w) {
        complication(c, cx, cy);
        String temp = w == null ? "—" : Math.round(w.temp) + "°";
        text.setTypeface(serif);
        text.setTextSize(36f);
        text.setLetterSpacing(0f);
        float tw = text.measureText(temp);
        float x0 = cx - (32f + 5f + tw) / 2f;
        icon(c, w == null ? R.drawable.ic_cloud : Weather.icon(w.code, w.isDay), x0 + 16f, cy - 19f, 32f);
        text.setColor(IVORY);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(temp, x0 + 37f, cy - 6f, text);
        text.setTextAlign(Paint.Align.CENTER);

        String hilo = w == null ? "" : "↑" + Math.round(w.hi) + "°  ↓" + Math.round(w.lo) + "°";
        label(c, hilo, cx, cy + 17f, 13f, sans, alpha(IVORY, 0.78f), 0f);
        String cond = w != null ? Weather.label(w.code)
                : Weather.hasLocation(getContext()) ? "LÄDT …" : "ORT FEHLT";
        label(c, cond, cx, cy + 35f, 10.5f, sansMedium, alpha(IVORY, 0.62f), 1.8f);
    }

    private void drawRain(Canvas c, float cx, float cy, Weather.Data w) {
        complication(c, cx, cy);
        int rain = w == null ? 0 : Math.max(0, Math.min(100, w.rain));
        rect.set(cx - 56f, cy - 56f, cx + 56f, cy + 56f);
        stroke.setStrokeWidth(8f);
        stroke.setColor(alpha(IVORY, 0.12f));
        c.drawArc(rect, 0f, 360f, false, stroke);
        if (rain > 0) {
            stroke.setColor(ACCENT);
            c.drawArc(rect, -90f, 360f * rain / 100f, false, stroke);
        }
        stroke.setStrokeWidth(1f);
        icon(c, R.drawable.ic_drop, cx, cy - 24f, 20f);
        label(c, w == null ? "—" : rain + " %", cx, cy + 9f, 24f, sansMedium, IVORY, 0f);
        label(c, "REGEN", cx, cy + 29f, 10.5f, sansMedium, alpha(IVORY, 0.62f), 1.8f);
    }

    private void drawDate(Canvas c, float cx, float cy, Calendar cal, long now) {
        complication(c, cx, cy);
        Date d = cal.getTime();
        String wd = weekdayFmt.format(d).replace(".", "").toUpperCase(Locale.GERMAN);
        String mo = monthFmt.format(d).replace(".", "").toUpperCase(Locale.GERMAN);
        label(c, wd, cx, cy - 26f, 12f, sansMedium, ACCENT, 2f);
        label(c, String.valueOf(cal.get(Calendar.DAY_OF_MONTH)), cx, cy + 16f, 44f, serif, IVORY, 0f);

        text.setTypeface(sansMedium);
        text.setTextSize(12f);
        text.setLetterSpacing(2f / 12f);
        float mw = text.measureText(mo);
        float x0 = cx - (14f + 6f + mw) / 2f;
        drawMoon(c, x0 + 7f, cy + 34f, 7f, Moon.phase(now));
        text.setColor(alpha(IVORY, 0.62f));
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(mo, x0 + 20f, cy + 38f, text);
        text.setTextAlign(Paint.Align.CENTER);
    }

    /** Moon disc with the lit part for phase p (0 = new, 0.5 = full), northern hemisphere. */
    private void drawMoon(Canvas c, float x, float y, float r, double p) {
        fill.setColor(alpha(IVORY, 0.16f));
        c.drawCircle(x, y, r, fill);
        if (p < 0.02 || p > 0.98) return;                      // new moon: only the dim disc
        double k = Math.cos(2 * Math.PI * p);                  // 1 = new, -1 = full
        float rx = Math.max(0.01f, (float) (r * Math.abs(k)));
        rect.set(x - r, y - r, x + r, y + r);
        rect2.set(x - rx, y - r, x + rx, y + r);
        path.reset();
        if (p < 0.5) {                                         // waxing: right side lit
            path.addArc(rect, -90f, 180f);
            path.arcTo(rect2, 90f, k > 0 ? -180f : 180f, false);
        } else {                                               // waning: left side lit
            path.addArc(rect, 90f, 180f);
            path.arcTo(rect2, -90f, k > 0 ? -180f : 180f, false);
        }
        path.close();
        fill.setColor(IVORY);
        c.drawPath(path, fill);
    }

    private void hand(Canvas c, float deg, float w, float len, float tail, int color, boolean outline) {
        c.save();
        c.rotate(deg);
        if (outline) {
            fill.setColor(DARK);
            rect.set(-w / 2f - 2f, -len - 2f, w / 2f + 2f, tail + 2f);
            c.drawRoundRect(rect, w / 2f + 2f, w / 2f + 2f, fill);
        }
        fill.setColor(color);
        rect.set(-w / 2f, -len, w / 2f, tail);
        c.drawRoundRect(rect, w / 2f, w / 2f, fill);
        c.restore();
    }

    /** Centre cap = Claude status: gray off, orange listening (halo), pulsing while active. */
    private void drawCenter(Canvas c) {
        float r = 9f;
        int color;
        if ("active".equals(claudeState)) {
            long t = SystemClock.uptimeMillis();
            for (int i = 0; i < 2; i++) {
                float f = ((t + i * 700) % 1400) / 1400f;
                stroke.setStrokeWidth(3f);
                stroke.setColor(alpha(ACCENT, 0.75f * (1f - f)));
                c.drawCircle(0f, 0f, r + 4f + f * 26f, stroke);
            }
            color = ACCENT;
        } else if ("listening".equals(claudeState)) {
            stroke.setStrokeWidth(2f);
            stroke.setColor(alpha(ACCENT, 0.45f));
            c.drawCircle(0f, 0f, r + 7f, stroke);
            color = ACCENT;
        } else {
            color = GRAY;
        }
        stroke.setStrokeWidth(1f);
        fill.setColor(DARK);
        c.drawCircle(0f, 0f, r + 2f, fill);
        fill.setColor(color);
        c.drawCircle(0f, 0f, r, fill);
        fill.setColor(DARK);
        c.drawCircle(0f, 0f, 3f, fill);
    }

    /** Lux -> bucket 0 (dark) .. 3 (bright), with hysteresis and a settle time against flicker. */
    private void updateLightBucket(long now) {
        float l = lux;
        if (lightSensor == null || l < 0) return;
        int want = 0;
        for (float bound : LUX_BOUNDS) if (l >= bound) want++;
        if (lightBucket >= 0 && want != lightBucket) {
            // Only move when clearly past the boundary (±40 %).
            int lo = Math.min(want, lightBucket);
            float bound = LUX_BOUNDS[lo];
            boolean up = want > lightBucket;
            if (up ? l < bound * 1.4f : l > bound * 0.6f) want = lightBucket;
        }
        if (lightBucket < 0) {
            lightBucket = want;
            android.util.Log.i("SpotClock", "room light " + Math.round(l) + " lux -> level " + want);
            return;
        }
        if (want == lightBucket) {
            pendingBucket = -1;
            return;
        }
        if (want != pendingBucket) {
            pendingBucket = want;
            pendingSince = now;
        } else if (now - pendingSince >= BUCKET_SETTLE_MS) {
            lightBucket = want;
            pendingBucket = -1;
            android.util.Log.i("SpotClock", "room light " + Math.round(l) + " lux -> level " + want);
        }
    }

    // ---------------------------------------------------------- now playing --

    /** Cover filling the round display, darkened towards the bottom for title/artist, progress on the rim. */
    private void drawNowPlaying(Canvas c, Calendar cal) {
        Bitmap art = NowPlaying.art;
        if (art != null) {
            if (art != artSource) {
                artSource = art;
                artScaled = centerCrop(art, 480);
            }
            c.drawBitmap(artScaled, -240f, -240f, bitmapPaint);
            fill.setShader(new LinearGradient(0f, -240f, 0f, 240f,
                    new int[]{0x99000000, 0x14000000, 0x33000000, 0xE6000000},
                    new float[]{0f, 0.3f, 0.5f, 0.85f}, Shader.TileMode.CLAMP));
            c.drawCircle(0f, 0f, 240f, fill);
            fill.setShader(null);
        } else {
            fill.setColor(alpha(ACCENT, 0.16f));
            c.drawCircle(0f, 0f, 240f, fill);
            label(c, "♪", 0f, 30f, 150f, serif, alpha(IVORY, 0.22f), 0f);
        }

        rect.set(-228f, -228f, 228f, 228f);
        stroke.setStrokeWidth(4f);
        stroke.setColor(alpha(IVORY, 0.2f));
        c.drawArc(rect, 0f, 360f, false, stroke);
        long dur = NowPlaying.duration;
        if (dur > 0) {
            float f = Math.max(0f, Math.min(1f, NowPlaying.currentPosition() / (float) dur));
            stroke.setColor(ACCENT);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(rect, -90f, 360f * f, false, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);
        }
        stroke.setStrokeWidth(1f);

        label(c, hhmm.format(cal.getTime()), 0f, -158f, 26f, sansMedium, IVORY, 0f);
        String title = NowPlaying.title == null ? "" : NowPlaying.title;
        label(c, fit(title, 350f, serif, 30f, 0f), 0f, 104f, 30f, serif, IVORY, 0f);
        String artist = NowPlaying.artist;
        if (artist != null && !artist.isEmpty()) {
            label(c, fit(artist.toUpperCase(Locale.GERMAN), 320f, sansMedium, 14f, 1.6f), 0f, 136f, 14f, sansMedium, alpha(IVORY, 0.78f), 1.6f);
        }
        if (!NowPlaying.playing) label(c, "PAUSE", 0f, 176f, 12f, sansMedium, alpha(IVORY, 0.6f), 2f);
    }

    private String fit(String s, float maxWidth, android.graphics.Typeface tf, float size, float spacingPx) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setLetterSpacing(spacingPx / size);
        return TextUtils.ellipsize(s, text, maxWidth, TextUtils.TruncateAt.END).toString();
    }

    private static Bitmap centerCrop(Bitmap b, int size) {
        int side = Math.min(b.getWidth(), b.getHeight());
        Bitmap sq = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
        return Bitmap.createScaledBitmap(sq, size, size, true);
    }

    // ------------------------------------------------------- timer / alarm --

    /** Countdown ring on the rim + remaining time at the top; alarm time at the bottom. */
    private void drawTimerAndAlarm(Canvas c, long now) {
        Context ctx = getContext();
        long end = Timers.timerEnd(ctx);
        if (end > 0) {
            long total = Math.max(1L, Timers.timerTotal(ctx));
            long left = end - now;
            float f = Math.max(0f, Math.min(1f, left / (float) total));
            rect.set(-235f, -235f, 235f, 235f);
            stroke.setStrokeWidth(7f);
            stroke.setColor(alpha(ACCENT, night ? 0.6f : 1f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(rect, -90f, 360f * f, false, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            stroke.setStrokeWidth(1f);
            long sec = Math.max(0L, (left + 999) / 1000);
            String t = sec >= 3600
                    ? String.format(Locale.GERMAN, "%d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60)
                    : String.format(Locale.GERMAN, "%d:%02d", sec / 60, sec % 60);
            fill.setColor(night ? Color.BLACK : DARK);
            rect.set(-46f, -212f, 46f, -184f);
            c.drawRoundRect(rect, 14f, 14f, fill);
            label(c, t, 0f, -192f, 18f, sansMedium, alpha(ACCENT, night ? 0.7f : 1f), 0.5f);
        }
        long at = Timers.alarmAt(ctx);
        if (at > 0) {
            label(c, "WECKER " + hhmm.format(new Date(at)), 0f, 203f, 12f, sansMedium, alpha(ACCENT, night ? 0.6f : 0.95f), 1.6f);
        }
    }

    /** Timer/alarm is ringing: pulsing rings, tap anywhere (or "Hey Claude, stopp") to stop. */
    private void drawRinging(Canvas c, Calendar cal) {
        long t = SystemClock.uptimeMillis();
        for (int i = 0; i < 3; i++) {
            float f = ((t + i * 400) % 1200) / 1200f;
            stroke.setStrokeWidth(6f);
            stroke.setColor(alpha(ACCENT, 0.6f * (1f - f)));
            c.drawCircle(0f, 0f, 80f + f * 150f, stroke);
        }
        stroke.setStrokeWidth(1f);
        fill.setColor(ACCENT);
        c.drawCircle(0f, 0f, 78f, fill);
        boolean timer = "timer".equals(RingService.ringing);
        label(c, timer ? "TIMER" : "WECKER", 0f, 9f, 24f, sansMedium, DARK, 2f);
        label(c, hhmm.format(cal.getTime()), 0f, -150f, 26f, sansMedium, IVORY, 0f);
        label(c, "Tippen zum Beenden", 0f, 160f, 15f, sans, alpha(IVORY, 0.75f), 0.5f);
    }

    // ---------------------------------------------------------------- night --

    private void drawNight(Canvas c, Calendar cal) {
        int dim = alpha(ACCENT, 0.55f);
        fill.setColor(dim);
        for (int i = 0; i < 12; i++) {
            c.save();
            c.rotate(i * 30f);
            c.drawCircle(0f, -214f, 3f, fill);
            c.restore();
        }
        Weather.Data w = Weather.data;
        if (w != null) label(c, Math.round(w.temp) + "°", 0f, -100f, 30f, serif, dim, 0f);
        int h = cal.get(Calendar.HOUR);
        int m = cal.get(Calendar.MINUTE);
        hand(c, (h + m / 60f) * 30f, 8f, 140f, 16f, dim, false);
        hand(c, m * 6f, 6f, 210f, 16f, dim, false);
        fill.setColor(Color.BLACK);
        c.drawCircle(0f, 0f, 10f, fill);
        boolean on = "listening".equals(claudeState) || "active".equals(claudeState);
        fill.setColor(on ? dim : alpha(GRAY, 0.45f));
        c.drawCircle(0f, 0f, 7f, fill);
    }

    // -------------------------------------------------------------- helpers --

    private void label(Canvas c, String s, float x, float y, float size, Typeface tf, int color, float spacingPx) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setColor(color);
        text.setLetterSpacing(spacingPx / size);
        c.drawText(s, x, y, text);
    }

    private void icon(Canvas c, int resId, float cx, float cy, float size) {
        Drawable d = icons.get(resId);
        if (d == null) {
            d = getContext().getDrawable(resId);
            icons.put(resId, d);
        }
        if (d == null) return;
        d.setBounds(Math.round(cx - size / 2f), Math.round(cy - size / 2f),
                Math.round(cx + size / 2f), Math.round(cy + size / 2f));
        d.draw(c);
    }

    private static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(a * 255f) << 24);
    }
}
