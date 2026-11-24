package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Theme row's picker in Launcher settings: "Original" and every theme in the library (ThemeLibrary), each with
 * its screenshot. Thumbnails are decoded small, off the main thread, only while the picker is open; applying a
 * theme copies its files on a background thread too, then hands back to Home, which rebuilds with it.
 */
final class ThemePicker {
    interface Done { void applied(); }

    private ThemePicker() {}

    /** What the Theme row reads: the active library theme's name, Original, or Custom for one theme.sh pushed. */
    static String state(Activity activity, SharedPreferences choices) {
        File root = Config.dir(activity);
        if (!ThemeLibrary.hasActiveTheme(root)) return activity.getString(R.string.theme_original);
        String name = ThemeLibrary.activeName(root, choices.getString(ThemeLibrary.ACTIVE_KEY, null));
        return name != null ? ThemeLibrary.label(name) : activity.getString(R.string.theme_custom);
    }

    static void show(Activity activity, SharedPreferences choices, Done done) {
        File root = Config.dir(activity);
        List<String> names = ThemeLibrary.list(root);
        if (names.isEmpty()) {
            AlertDialog.Builder b = new AlertDialog.Builder(activity)
                    .setTitle(R.string.theme_label)
                    .setMessage(R.string.theme_library_empty)
                    .setNegativeButton(android.R.string.cancel, null);
            if (ThemeLibrary.hasActiveTheme(root)) {
                b.setPositiveButton(R.string.theme_original, (d, w) -> apply(activity, choices, root, null, done));
            }
            b.show();
            return;
        }
        String active = ThemeLibrary.hasActiveTheme(root)
                ? ThemeLibrary.activeName(root, choices.getString(ThemeLibrary.ACTIVE_KEY, null)) : "";
        Rows rows = new Rows(activity, root, names, active);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.theme_label)
                .setAdapter(rows, (d, which) -> apply(activity, choices, root,
                        which == 0 ? null : names.get(which - 1), done))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnDismissListener(d -> rows.close());
        dialog.show();
        // Open on the active theme, so OK straight away changes nothing.
        int at = active == null ? -1 : active.isEmpty() ? 0 : names.indexOf(active) + 1;
        if (at > 0) dialog.getListView().setSelection(at);
    }

    /** @param name the library theme to apply, or null for Original */
    private static void apply(Activity activity, SharedPreferences choices, File root, String name, Done done) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            String revision = null;
            boolean ok;
            try {
                if (name == null) ThemeLibrary.reset(root);
                else revision = ThemeLibrary.apply(root, name);
                ok = true;
            } catch (IOException e) {
                ok = false;
            }
            final boolean applied = ok;
            final String token = revision;
            main.post(() -> {
                if (!applied) {
                    Toast.makeText(activity, R.string.theme_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                if (token == null) choices.edit().remove(ThemeLibrary.ACTIVE_KEY).apply();
                else choices.edit().putString(ThemeLibrary.ACTIVE_KEY, name + " " + token).apply();
                Toast.makeText(activity, R.string.theme_applied, Toast.LENGTH_SHORT).show();
                done.applied();
            });
        }, "theme-apply").start();
    }

    /** Row 0 is Original; row i is names[i - 1]. */
    private static final class Rows extends BaseAdapter {
        private final Activity activity;
        private final File root;
        private final List<String> names;
        private final String active;
        private final Bitmap[] thumbs;
        private final boolean[] asked;
        private final ExecutorService decoder = Executors.newSingleThreadExecutor();
        private final Handler main = new Handler(Looper.getMainLooper());
        private final int thumbWidth;

        Rows(Activity activity, File root, List<String> names, String active) {
            this.activity = activity;
            this.root = root;
            this.names = names;
            this.active = active;
            this.thumbs = new Bitmap[names.size() + 1];
            this.asked = new boolean[names.size() + 1];
            this.thumbWidth = Math.round(152 * activity.getResources().getDisplayMetrics().density);
        }

        @Override public int getCount() { return names.size() + 1; }
        @Override public Object getItem(int position) { return position == 0 ? null : names.get(position - 1); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) view = LayoutInflater.from(activity).inflate(R.layout.theme_picker_row, parent, false);
            ImageView thumb = view.findViewById(R.id.themeThumb);
            TextView label = view.findViewById(R.id.themeName);
            TextView mark = view.findViewById(R.id.themeActive);
            String name = position == 0 ? null : names.get(position - 1);
            label.setText(name == null ? activity.getString(R.string.theme_original) : ThemeLibrary.label(name));
            boolean on = name == null ? "".equals(active) : name.equals(active);
            mark.setText(on ? activity.getString(R.string.theme_active) : "");
            thumb.setImageBitmap(thumbs[position]);
            if (name != null && !asked[position]) {
                asked[position] = true;
                File shot = ThemeLibrary.screenshot(root, name);
                if (shot != null) decoder.execute(() -> {
                    Bitmap bmp = decode(shot, thumbWidth);
                    main.post(() -> {
                        thumbs[position] = bmp;
                        notifyDataSetChanged();
                    });
                });
            }
            return view;
        }

        void close() {
            decoder.shutdownNow();
        }

        /** A screenshot cut down to about the thumbnail's width, in RGB_565: a few hundred KB at most per theme. */
        private static Bitmap decode(File file, int width) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= width) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try {
                return BitmapFactory.decodeFile(file.getPath(), o);
            } catch (OutOfMemoryError e) {
                return null;
            }
        }
    }
}
