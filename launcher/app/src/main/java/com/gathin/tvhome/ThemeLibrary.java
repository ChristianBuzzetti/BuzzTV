package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Themes kept on the TV under {@code Config.dir/themes/<name>/}, each laid out like a folder in themes/, so the
 * Theme row in Launcher settings can switch between them without a computer. Applying one copies its files over
 * the active theme (config.json, wallpaper.jpg, wallpapers/) exactly as tools/theme.sh would, and writes a new
 * theme_revision so Config drops the on-TV choices made for the previous theme. Home reads nothing from here:
 * the library costs nothing at start-up, only when the picker opens or a theme is applied.
 */
final class ThemeLibrary {
    static final String DIR = "themes";
    static final String SCREENSHOT = "screenshot.jpg";
    /** SharedPreferences key (Config.SETTINGS_NAME) holding the library theme last applied, for the picker's mark. */
    static final String ACTIVE_KEY = "library_theme";
    private static final String WALLPAPER = "wallpaper.jpg";
    private static final String REVISION = "theme_revision";
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,39}");

    private ThemeLibrary() {}

    /** A folder name the library takes: lower case letters, digits, '-' and '_', at most 40, no leading mark. */
    static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** The themes on the TV that can be applied (a valid name and a config.json), sorted by name. */
    static List<String> list(File root) {
        List<String> names = new ArrayList<>();
        File[] dirs = root == null ? null : new File(root, DIR).listFiles(File::isDirectory);
        if (dirs == null) return names;
        for (File d : dirs) {
            if (validName(d.getName()) && new File(d, Config.FILE_NAME).isFile()) names.add(d.getName());
        }
        Collections.sort(names);
        return names;
    }

    /** "midnight-glass" reads "Midnight glass". */
    static String label(String name) {
        String spaced = name.replace('-', ' ').replace('_', ' ').trim();
        if (spaced.isEmpty()) return name;
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    /** The theme's screenshot.jpg, or null when it has none. */
    static File screenshot(File root, String name) {
        if (root == null || !validName(name)) return null;
        File f = new File(new File(new File(root, DIR), name), SCREENSHOT);
        return f.isFile() ? f : null;
    }

    /**
     * Makes the library theme {@code name} the active theme. Its files replace the active ones; nothing else moves.
     * @return the new theme_revision, which tells this theme apart from one pushed later by tools/theme.sh
     */
    static String apply(File root, String name) throws IOException {
        if (root == null || !validName(name)) throw new IOException("no such theme: " + name);
        File src = new File(new File(root, DIR), name);
        File config = new File(src, Config.FILE_NAME);
        if (!config.isFile()) throw new IOException("no config.json in " + src);
        copy(config, new File(root, Config.FILE_NAME));
        File wallpaper = new File(src, WALLPAPER);
        if (wallpaper.isFile()) copy(wallpaper, new File(root, WALLPAPER));
        else delete(new File(root, WALLPAPER));
        File target = new File(root, ThemeFiles.WALLPAPERS);
        clearCollection(target);
        File[] images = new File(src, ThemeFiles.WALLPAPERS).listFiles();
        if (images != null) {
            for (File f : images) {
                String n = f.getName();
                if (f.isFile() && (ThemeFiles.collectionImage(n) || WallpaperCaptions.FILE_NAME.equals(n))) {
                    copy(f, new File(target, n));
                }
            }
        }
        return newRevision(root);
    }

    /** Back to the launcher's built-in look: the active theme's files go, the library stays. */
    static void reset(File root) throws IOException {
        if (root == null) throw new IOException("no theme folder");
        delete(new File(root, Config.FILE_NAME));
        delete(new File(root, WALLPAPER));
        clearCollection(new File(root, ThemeFiles.WALLPAPERS));
        newRevision(root);
    }

    /** Removes a theme from the library; the active theme is not touched even when it came from it. */
    static boolean remove(File root, String name) {
        if (root == null || !validName(name)) return false;
        return deleteTree(new File(new File(root, DIR), name));
    }

    static boolean deleteTree(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteTree(c);
        return f.delete();
    }

    private static void clearCollection(File dir) throws IOException {
        File[] old = dir.listFiles();
        if (old == null) return;
        for (File f : old) {
            String n = f.getName();
            if (f.isFile() && (ThemeFiles.collectionImage(n) || WallpaperCaptions.FILE_NAME.equals(n))) delete(f);
        }
    }

    /** True when a theme is active, from the library or pushed by tools/theme.sh. */
    static boolean hasActiveTheme(File root) {
        return root != null && new File(root, Config.FILE_NAME).isFile();
    }

    /** The theme_revision on the TV now, or "" when there is none. */
    static String currentRevision(File root) {
        File f = root == null ? null : new File(root, REVISION);
        if (f == null || !f.isFile() || f.length() > 64) return "";
        byte[] bytes = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int n = in.read(bytes);
            return n > 0 ? new String(bytes, 0, n, StandardCharsets.US_ASCII).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** The stored "name revision" pair: the active library theme's name while nothing replaced it, else null. */
    static String activeName(File root, String stored) {
        if (stored == null || !hasActiveTheme(root)) return null;
        int space = stored.indexOf(' ');
        if (space <= 0) return null;
        String name = stored.substring(0, space);
        String revision = stored.substring(space + 1);
        return validName(name) && !revision.isEmpty() && revision.equals(currentRevision(root)) ? name : null;
    }

    private static String newRevision(File root) throws IOException {
        String token = UUID.randomUUID().toString().replace("-", "");
        File tmp = new File(root, REVISION + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(token.getBytes(StandardCharsets.US_ASCII));
        }
        rename(tmp, new File(root, REVISION));
        return token;
    }

    /** Copies through a temporary file and a rename, so Home never reads half a picture or half a config. */
    private static void copy(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
        File tmp = new File(parent, to.getName() + ".tmp");
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(tmp)) {
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (IOException e) {
            tmp.delete();
            throw e;
        }
        rename(tmp, to);
    }

    private static void rename(File tmp, File to) throws IOException {
        if (tmp.renameTo(to)) return;
        // Some FUSE mounts refuse a rename over an existing file.
        delete(to);
        if (!tmp.renameTo(to)) {
            tmp.delete();
            throw new IOException("cannot write " + to);
        }
    }

    private static void delete(File f) throws IOException {
        if (f.exists() && !f.delete()) throw new IOException("cannot delete " + f);
    }
}
