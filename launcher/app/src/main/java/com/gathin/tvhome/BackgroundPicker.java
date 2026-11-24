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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Background row's picker in Launcher settings (BuzzTV). First list: every background at random, then each
 * section of the library with its picture. Picking a section opens its pictures: the section at random, or one
 * picture. There is no "none": a background always has a picture. Nothing is copied; the choice is saved in the
 * launcher's preferences (BackgroundLibrary) and Home rebuilds with it once these settings close.
 */
final class BackgroundPicker {
    interface Done { void picked(); }

    private BackgroundPicker() {}

    /** What the Background row reads. */
    static String state(Activity activity, SharedPreferences choices) {
        String mode = choices.getString(BackgroundLibrary.MODE_KEY, null);
        String section = choices.getString(BackgroundLibrary.SECTION_KEY, null);
        if (BackgroundLibrary.MODE_ALL.equals(mode)) return activity.getString(R.string.background_random_all);
        if (mode == null || BackgroundLibrary.resolve(Config.dir(activity), mode, section,
                choices.getString(BackgroundLibrary.IMAGE_KEY, null)).isEmpty()) {
            return activity.getString(R.string.background_default);
        }
        String label = ThemeLibrary.label(section);
        return BackgroundLibrary.MODE_SECTION.equals(mode)
                ? activity.getString(R.string.background_random_in, label) : label;
    }

    static void show(Activity activity, SharedPreferences choices, Done done) {
        File root = Config.dir(activity);
        List<String> sections = BackgroundLibrary.sections(root);
        if (sections.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.theme_label)
                    .setMessage(R.string.theme_library_empty)
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        String mode = choices.getString(BackgroundLibrary.MODE_KEY, null);
        String current = choices.getString(BackgroundLibrary.SECTION_KEY, null);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(activity.getString(R.string.background_random_all), null,
                BackgroundLibrary.MODE_ALL.equals(mode)));
        for (String s : sections) {
            List<File> images = BackgroundLibrary.images(root, s);
            rows.add(new Row(ThemeLibrary.label(s) + "  (" + images.size() + ")", images.get(0),
                    s.equals(current) && !BackgroundLibrary.MODE_ALL.equals(mode)));
        }
        open(activity, activity.getString(R.string.theme_label), rows, which -> {
            if (which == 0) save(activity, choices, BackgroundLibrary.MODE_ALL, null, null, done);
            else showSection(activity, choices, root, sections.get(which - 1), done);
        });
    }

    /** One section's pictures, after "at random" for the whole section. */
    private static void showSection(Activity activity, SharedPreferences choices, File root, String section, Done done) {
        List<File> images = BackgroundLibrary.images(root, section);
        if (images.isEmpty()) return;
        String label = ThemeLibrary.label(section);
        String mode = choices.getString(BackgroundLibrary.MODE_KEY, null);
        boolean here = section.equals(choices.getString(BackgroundLibrary.SECTION_KEY, null));
        String image = choices.getString(BackgroundLibrary.IMAGE_KEY, null);
        Map<String, String> captions = WallpaperCaptions.read(new File(images.get(images.size() - 1).getParentFile().getPath()));
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(activity.getString(R.string.background_random_in, label), images.get(0),
                here && BackgroundLibrary.MODE_SECTION.equals(mode)));
        for (File f : images) {
            rows.add(new Row(BackgroundLibrary.imageLabel(f, captions.get(f.getName())), f,
                    here && BackgroundLibrary.MODE_IMAGE.equals(mode) && f.getName().equals(image)));
        }
        open(activity, label, rows, which -> {
            if (which == 0) save(activity, choices, BackgroundLibrary.MODE_SECTION, section, null, done);
            else save(activity, choices, BackgroundLibrary.MODE_IMAGE, section, images.get(which - 1).getName(), done);
        });
    }

    private interface Pick { void row(int which); }

    private static void open(Activity activity, String title, List<Row> rows, Pick pick) {
        Rows adapter = new Rows(activity, rows);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setAdapter(adapter, (d, which) -> pick.row(which))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnDismissListener(d -> adapter.close());
        dialog.show();
        // Open on the current choice, so OK straight away changes nothing.
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).active) {
                dialog.getListView().setSelection(i);
                break;
            }
        }
    }

    private static void save(Activity activity, SharedPreferences choices, String mode, String section, String image,
                             Done done) {
        SharedPreferences.Editor e = choices.edit().putString(BackgroundLibrary.MODE_KEY, mode)
                .remove(Config.WALLPAPER_CHOICE_KEY);
        if (section == null) e.remove(BackgroundLibrary.SECTION_KEY);
        else e.putString(BackgroundLibrary.SECTION_KEY, section);
        if (image == null) e.remove(BackgroundLibrary.IMAGE_KEY);
        else e.putString(BackgroundLibrary.IMAGE_KEY, image);
        e.apply();
        Toast.makeText(activity, R.string.theme_applied, Toast.LENGTH_SHORT).show();
        done.picked();
    }

    private static final class Row {
        final String label;
        final File thumb;
        final boolean active;

        Row(String label, File thumb, boolean active) {
            this.label = label;
            this.thumb = thumb;
            this.active = active;
        }
    }

    /** Thumbnails are decoded small, off the main thread, only while the picker is open. */
    private static final class Rows extends BaseAdapter {
        private final Activity activity;
        private final List<Row> rows;
        private final Bitmap[] thumbs;
        private final boolean[] asked;
        private final ExecutorService decoder = Executors.newSingleThreadExecutor();
        private final Handler main = new Handler(Looper.getMainLooper());
        private final int thumbWidth;

        Rows(Activity activity, List<Row> rows) {
            this.activity = activity;
            this.rows = rows;
            this.thumbs = new Bitmap[rows.size()];
            this.asked = new boolean[rows.size()];
            this.thumbWidth = Math.round(152 * activity.getResources().getDisplayMetrics().density);
        }

        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) view = LayoutInflater.from(activity).inflate(R.layout.theme_picker_row, parent, false);
            ImageView thumb = view.findViewById(R.id.themeThumb);
            TextView label = view.findViewById(R.id.themeName);
            TextView mark = view.findViewById(R.id.themeActive);
            Row row = rows.get(position);
            label.setText(row.label);
            mark.setText(row.active ? activity.getString(R.string.theme_active) : "");
            thumb.setImageBitmap(thumbs[position]);
            if (row.thumb != null && !asked[position]) {
                asked[position] = true;
                decoder.execute(() -> {
                    Bitmap bmp = decode(row.thumb, thumbWidth);
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

        /** A picture cut down to about the thumbnail's width, in RGB_565: a few hundred KB at most. */
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
