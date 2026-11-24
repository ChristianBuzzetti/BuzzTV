package com.gathin.tvhome;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BuzzTV backgrounds: every picture in the on-TV library ({@code Config.dir/themes/<section>/wallpaper.jpg} and
 * {@code wallpapers/}), chosen in Launcher settings on its own, apart from the rest of the look. A choice is one
 * picture, a whole section at random (a section is one library folder: Christmas, Patagonia, ...), or every
 * picture at random. It is kept in the launcher's preferences and read straight from the library, so nothing is
 * copied and no other setting moves when the background changes.
 */
final class BackgroundLibrary {
    /** Preference keys (Config.SETTINGS_NAME). No mode stored: the active theme's own wallpaper, as before. */
    static final String MODE_KEY = "background_mode";
    static final String SECTION_KEY = "background_section";
    static final String IMAGE_KEY = "background_image";
    static final String MODE_IMAGE = "image";
    static final String MODE_SECTION = "section";
    static final String MODE_ALL = "all";
    /** How often a random background changes when its section does not say (wallpaper_rotation_minutes). */
    static final int DEFAULT_ROTATION_MINUTES = 20;
    private static final String SINGLE = "wallpaper.jpg";

    private BackgroundLibrary() {}

    /** The library folders that hold at least one picture, sorted by name. */
    static List<String> sections(File root) {
        List<String> out = new ArrayList<>();
        File[] dirs = root == null ? null : new File(root, ThemeLibrary.DIR).listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) {
            if (ThemeLibrary.validName(d.getName()) && !images(root, d.getName()).isEmpty()) out.add(d.getName());
        }
        Collections.sort(out);
        return out;
    }

    /** A section's pictures: its wallpaper.jpg first, then its wallpapers/ collection by name. */
    static List<File> images(File root, String section) {
        List<File> out = new ArrayList<>();
        if (root == null || !ThemeLibrary.validName(section)) return out;
        File dir = new File(new File(root, ThemeLibrary.DIR), section);
        File single = new File(dir, SINGLE);
        if (single.isFile() && single.length() > 0) out.add(single);
        out.addAll(WallpaperRotation.images(new File(dir, ThemeFiles.WALLPAPERS)));
        return out;
    }

    /**
     * The pictures a stored choice stands for, or an empty list when there is no choice or it no longer matches
     * the library (a removed section or picture), which leaves the active theme's wallpaper in place.
     */
    static List<File> resolve(File root, String mode, String section, String image) {
        if (MODE_ALL.equals(mode)) {
            List<File> all = new ArrayList<>();
            for (String s : sections(root)) all.addAll(images(root, s));
            return all;
        }
        if (MODE_SECTION.equals(mode)) return images(root, section);
        if (MODE_IMAGE.equals(mode)) {
            for (File f : images(root, section)) {
                if (f.getName().equals(image)) return Collections.singletonList(f);
            }
        }
        return Collections.emptyList();
    }

    /** The section whose own settings (dim, motion) apply: the chosen one, or none for every picture at random. */
    static String settingsSection(String mode, String section) {
        return MODE_SECTION.equals(mode) || MODE_IMAGE.equals(mode) ? section : null;
    }

    /**
     * A picture's name for the picker: its caption from wallpapers/captions.txt without the credit after " · "
     * ("Sleigh over a snowy night · CleanYourTV, CC0" reads "Sleigh over a snowy night"), else its file name.
     */
    static String imageLabel(File image, String caption) {
        if (caption != null) {
            int credit = caption.indexOf(" · ");
            String name = (credit > 0 ? caption.substring(0, credit) : caption).trim();
            if (!name.isEmpty()) return name;
        }
        String n = image.getName();
        int dot = n.lastIndexOf('.');
        return ThemeLibrary.label(dot > 0 ? n.substring(0, dot) : n);
    }
}
