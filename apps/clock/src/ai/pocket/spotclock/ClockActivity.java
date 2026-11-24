package ai.pocket.spotclock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/**
 * The clock as an app (tap to close). Also the place to set the weather location:
 *   adb shell am start -n ai.pocket.spotclock/.ClockActivity --es lat 49.17 --es lon 9.27 --es place Erlenbach
 */
public class ClockActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleConfig(getIntent());
        if (isHome()) ensureWallpaper();
        ClockView view = new ClockView(this);
        view.setNightListener(new ClockView.NightListener() {
            @Override
            public void onNightChanged(boolean night, float brightness) {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = brightness < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : brightness;
                getWindow().setAttributes(lp);
            }
        });
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (RingService.ringing != null) RingService.stop(ClockActivity.this);
                else if (isHome()) openApps();
                else finish();
            }
        });
        setContentView(view);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleConfig(intent);
    }

    @Override
    public void onBackPressed() {
        if (!isHome()) super.onBackPressed();   // as home screen there is nothing to go back to
    }

    /** True when the system started us as the home screen (not from the app list). */
    private boolean isHome() {
        Intent i = getIntent();
        return i != null && i.hasCategory(Intent.CATEGORY_HOME);
    }

    /** Opens our own app screen (AppsActivity); its "Uhr" button or Home brings the clock back. */
    private void openApps() {
        startActivity(new Intent(this, AppsActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /** One-time: plain dark wallpaper (seen behind the lock screen and in recents) to match the clock. */
    private void ensureWallpaper() {
        android.content.SharedPreferences p = Weather.prefs(this);
        if (p.getBoolean("wallpaper_v1", false)) return;
        try {
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(480, 480, android.graphics.Bitmap.Config.ARGB_8888);
            bmp.eraseColor(ClockView.DARK);
            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(this);
            wm.setBitmap(bmp, null, true, android.app.WallpaperManager.FLAG_SYSTEM | android.app.WallpaperManager.FLAG_LOCK);
            p.edit().putBoolean("wallpaper_v1", true).apply();
            Log.i("SpotClock", "dark wallpaper set");
        } catch (Exception e) {
            Log.w("SpotClock", "wallpaper failed", e);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void handleConfig(Intent intent) {
        if (intent == null) return;
        String lat = intent.getStringExtra("lat");
        String lon = intent.getStringExtra("lon");
        if (lat == null || lon == null) return;
        boolean ok = Weather.setLocation(this, lat, lon, intent.getStringExtra("place"));
        Log.i("SpotClock", ok ? "location set: " + lat + "," + lon : "invalid location: " + lat + "," + lon);
    }
}
