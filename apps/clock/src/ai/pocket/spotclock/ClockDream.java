package ai.pocket.spotclock;

import android.service.dreams.DreamService;

/** Screensaver: shown whenever the Spot is idle on power. A tap ends it. */
public class ClockDream extends DreamService {
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        ClockView view = new ClockView(this);
        view.setNightListener(new ClockView.NightListener() {
            @Override
            public void onNightChanged(boolean night, float brightness) {
                setScreenBright(true);
                android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = brightness < 0 ? android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : brightness;
                getWindow().setAttributes(lp);
            }
        });
        setContentView(view);
    }
}
