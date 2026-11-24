package ai.pocket.spotclock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * App screen in the clock's look: dark, three large icons per row inside the round display,
 * favourites first. Opened by tapping the clock; the button at the bottom goes back.
 * Sizes are in the clock's 480-unit space so they don't depend on the display density.
 */
public class AppsActivity extends Activity {
    private static final List<String> FAVOURITES = Arrays.asList(
            "com.anthropic.claude", "com.openai.chatgpt", "com.spotify.music", "ai.pocket.heyclaude",
            "com.aurora.store", "com.android.settings");

    private float unit;   // px per 1/480 of the screen width

    private int s(float v) {
        return Math.round(v * unit);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int w = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        unit = w / 480f;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ClockView.DARK);

        GridView grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(s(8));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setClipToPadding(false);
        grid.setPadding(0, s(64), 0, s(104));
        grid.setVerticalFadingEdgeEnabled(true);
        grid.setFadingEdgeLength(s(56));
        final List<ResolveInfo> apps = loadApps();
        grid.setAdapter(new AppAdapter(this, apps));
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(apps.get(position));
            }
        });
        // Inscribed in the round screen: ~340 units wide, centred.
        root.addView(grid, new FrameLayout.LayoutParams(s(348), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

        TextView back = new TextView(this);
        back.setText(R.string.back_to_clock);
        back.setSingleLine(true);
        back.setTextColor(ClockView.IVORY);
        back.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        back.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, s(15));
        back.setLetterSpacing(0.12f);
        back.setGravity(Gravity.CENTER);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFF242422);                       // opaque: icons scroll underneath
        pill.setStroke(Math.max(1, s(1.5f)), 0x59FAF9F5);
        pill.setCornerRadius(s(24));
        back.setBackground(pill);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        // 140 units wide like before; longer translations ("Back to clock") widen the pill
        // instead of wrapping (still well inside the round screen at this height).
        back.setMinWidth(s(140));
        back.setPadding(s(22), 0, s(22), 0);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, s(48), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = s(34);
        root.addView(back, lp);

        setContentView(root);
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

    private List<ResolveInfo> loadApps() {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(main, 0);
        List<ResolveInfo> apps = new ArrayList<>();
        for (ResolveInfo ri : all) {
            if (!getPackageName().equals(ri.activityInfo.packageName)) apps.add(ri);
        }
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                int fa = FAVOURITES.indexOf(a.activityInfo.packageName);
                int fb = FAVOURITES.indexOf(b.activityInfo.packageName);
                if (fa < 0) fa = Integer.MAX_VALUE;
                if (fb < 0) fb = Integer.MAX_VALUE;
                if (fa != fb) return fa < fb ? -1 : 1;
                return a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
            }
        });
        return apps;
    }

    private void launch(ResolveInfo ri) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            finish();   // Home / Back from the app lands on the clock again
        } catch (Exception e) {
            Log.w("SpotClock", "cannot start " + ri.activityInfo.packageName, e);
        }
    }

    private final class AppAdapter extends BaseAdapter {
        private final Context ctx;
        private final List<ResolveInfo> apps;
        private final PackageManager pm;

        AppAdapter(Context ctx, List<ResolveInfo> apps) {
            this.ctx = ctx;
            this.apps = apps;
            this.pm = ctx.getPackageManager();
        }

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            } else {
                cell = new LinearLayout(ctx);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(0, s(6), 0, s(6));
                icon = new ImageView(ctx);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(icon, new LinearLayout.LayoutParams(s(72), s(72)));
                label = new TextView(ctx);
                label.setTextColor(0xD9FAF9F5);
                label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, s(14));
                label.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
                label.setGravity(Gravity.CENTER_HORIZONTAL);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                llp.topMargin = s(8);
                llp.leftMargin = s(4);
                llp.rightMargin = s(4);
                cell.addView(label, llp);
            }
            ResolveInfo ri = apps.get(position);
            icon.setImageDrawable(ri.loadIcon(pm));
            label.setText(ri.loadLabel(pm));
            return cell;
        }
    }
}
