package ai.pocket.spotclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Today's weather from Open-Meteo (free, no API key). Fetched at most every
 * 15 minutes, cached in SharedPreferences so the clock has data right after a
 * restart. The location (lat/lon) is set once via ClockActivity extras; there is no
 * default location, so without one nothing is fetched and the face shows placeholders.
 */
final class Weather {
    private static final String TAG = "SpotClock";
    private static final long MAX_AGE_MS = 15 * 60 * 1000L;
    private static final long RETRY_MS = 60 * 1000L;

    static final class Data {
        double temp, hi, lo;
        int code, rain;
        boolean isDay;
        long sunrise, sunset, sunriseTomorrow, fetchedAt;
    }

    static volatile Data data;
    private static volatile boolean fetching;
    private static volatile long lastTry;

    private Weather() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("spotclock", Context.MODE_PRIVATE);
    }

    static boolean hasLocation(Context c) {
        return prefs(c).contains("lat") && prefs(c).contains("lon");
    }

    /** Validates and stores a location; returns false if lat/lon are not numbers in range. */
    static boolean setLocation(Context c, String lat, String lon, String place) {
        double la, lo;
        try {
            la = Double.parseDouble(lat.trim());
            lo = Double.parseDouble(lon.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (la < -90 || la > 90 || lo < -180 || lo > 180) return false;
        prefs(c).edit()
                .putString("lat", String.format(Locale.ROOT, "%.4f", la))
                .putString("lon", String.format(Locale.ROOT, "%.4f", lo))
                .putString("place", place == null ? "" : place)
                .remove("weather_json")
                .remove("weather_at")
                .apply();
        data = null;
        lastTry = 0;
        return true;
    }

    static void load(Context c) {
        if (data != null) return;
        SharedPreferences p = prefs(c);
        String json = p.getString("weather_json", null);
        if (json == null) return;
        try {
            data = parse(json, p.getLong("weather_at", 0));
        } catch (Exception e) {
            Log.w(TAG, "cached weather unreadable: " + e);
        }
    }

    /** Starts a background fetch if the data is stale; {@code done} runs on that thread. */
    static void maybeFetch(Context c, final Runnable done) {
        if (fetching || !hasLocation(c)) return;
        long now = System.currentTimeMillis();
        Data d = data;
        if (d != null && now - d.fetchedAt < MAX_AGE_MS) return;
        if (now - lastTry < RETRY_MS) return;
        lastTry = now;
        fetching = true;
        SharedPreferences p = prefs(c);
        final String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + p.getString("lat", "") + "&longitude=" + p.getString("lon", "")
                + "&current=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
                + "&timezone=auto&forecast_days=2";
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                fetch(app, url, done);
            }
        }, "spotclock-weather").start();
    }

    private static void fetch(Context app, String url, Runnable done) {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                con.setRequestProperty("User-Agent", "SpotClock/1.0");
                String body;
                try (InputStream in = con.getInputStream()) {
                    body = readAll(in);
                }
                long at = System.currentTimeMillis();
                Data fresh = parse(body, at);
                prefs(app).edit().putString("weather_json", body).putLong("weather_at", at).apply();
                data = fresh;
                if (done != null) done.run();
            } catch (Exception e) {
                Log.w(TAG, "weather fetch failed: " + e);
            } finally {
                if (con != null) con.disconnect();
                fetching = false;
            }
    }

    static Data parse(String body, long fetchedAt) throws JSONException, ParseException {
        JSONObject o = new JSONObject(body);
        JSONObject cur = o.getJSONObject("current");
        JSONObject daily = o.getJSONObject("daily");
        Data r = new Data();
        r.temp = cur.getDouble("temperature_2m");
        r.code = cur.getInt("weather_code");
        r.isDay = cur.optInt("is_day", 1) == 1;
        r.hi = daily.getJSONArray("temperature_2m_max").getDouble(0);
        r.lo = daily.getJSONArray("temperature_2m_min").getDouble(0);
        r.rain = daily.getJSONArray("precipitation_probability_max").optInt(0, 0);
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone(o.optString("timezone", TimeZone.getDefault().getID())));
        JSONArray sunrise = daily.getJSONArray("sunrise");
        r.sunrise = f.parse(sunrise.getString(0)).getTime();
        r.sunset = f.parse(daily.getJSONArray("sunset").getString(0)).getTime();
        r.sunriseTomorrow = sunrise.length() > 1 ? f.parse(sunrise.getString(1)).getTime() : 0;
        r.fetchedAt = fetchedAt;
        return r;
    }

    /** WMO weather code -> string resource with a short label (upper case, fits a complication). */
    static int label(int code) {
        if (code == 0) return R.string.wx_clear;
        if (code == 1) return R.string.wx_mainly_clear;
        if (code == 2) return R.string.wx_partly_cloudy;
        if (code == 3) return R.string.wx_overcast;
        if (code == 45 || code == 48) return R.string.wx_fog;
        if (code >= 51 && code <= 57) return R.string.wx_drizzle;
        if (code >= 61 && code <= 67) return R.string.wx_rain;
        if (code >= 71 && code <= 77) return R.string.wx_snow;
        if (code >= 80 && code <= 82) return R.string.wx_showers;
        if (code == 85 || code == 86) return R.string.wx_snow_showers;
        if (code >= 95) return R.string.wx_thunderstorm;
        return R.string.wx_unknown;
    }

    static int icon(int code, boolean isDay) {
        if (code <= 1) return isDay ? R.drawable.ic_sun : R.drawable.ic_moon_night;
        if (code == 2) return isDay ? R.drawable.ic_partly : R.drawable.ic_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_fog;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return R.drawable.ic_rain;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_snow;
        if (code >= 95) return R.drawable.ic_storm;
        return R.drawable.ic_cloud;
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
