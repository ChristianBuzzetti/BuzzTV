package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ThemeLibraryTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void namesFollowTheFolderRule() {
        assertTrue(ThemeLibrary.validName("gamer"));
        assertTrue(ThemeLibrary.validName("istanbul_v2"));
        assertTrue(ThemeLibrary.validName("midnight-glass"));
        assertFalse(ThemeLibrary.validName("Gamer"));
        assertFalse(ThemeLibrary.validName("-x"));
        assertFalse(ThemeLibrary.validName(".."));
        assertFalse(ThemeLibrary.validName("a/b"));
        assertFalse(ThemeLibrary.validName(""));
        assertFalse(ThemeLibrary.validName(null));
        assertTrue(ThemeLibrary.validName("a234567890123456789012345678901234567890"));
        assertFalse(ThemeLibrary.validName("a2345678901234567890123456789012345678901"));
    }

    @Test public void labelsReadAsWords() {
        assertEquals("Midnight glass", ThemeLibrary.label("midnight-glass"));
        assertEquals("Istanbul v2", ThemeLibrary.label("istanbul_v2"));
    }

    @Test public void listsOnlyThemesWithAConfig() throws IOException {
        File root = tmp.getRoot();
        write(new File(root, "themes/b-theme/config.json"), "{}");
        write(new File(root, "themes/a-theme/config.json"), "{}");
        write(new File(root, "themes/empty/screenshot.jpg"), "x");
        write(new File(root, "themes/Bad/config.json"), "{}");
        assertEquals(Arrays.asList("a-theme", "b-theme"), ThemeLibrary.list(root));
        assertEquals(Collections.emptyList(), ThemeLibrary.list(new File(root, "missing")));
        assertEquals(Collections.emptyList(), ThemeLibrary.list(null));
    }

    @Test public void applyReplacesTheActiveThemeFiles() throws IOException {
        File root = tmp.getRoot();
        // The active theme: a config, a single wallpaper and an old collection.
        write(new File(root, "config.json"), "{\"columns\":4}");
        write(new File(root, "wallpaper.jpg"), "old-single");
        write(new File(root, "wallpapers/old.jpg"), "old");
        write(new File(root, "wallpapers/captions.txt"), "old.jpg|Old");
        write(new File(root, "wallpapers/keep.txt"), "not a theme file");
        // The library theme: a collection and no single wallpaper.
        write(new File(root, "themes/gamer/config.json"), "{\"columns\":6}");
        write(new File(root, "themes/gamer/screenshot.jpg"), "shot");
        write(new File(root, "themes/gamer/wallpapers/a.jpg"), "A");
        write(new File(root, "themes/gamer/wallpapers/captions.txt"), "a.jpg|A");

        String revision = ThemeLibrary.apply(root, "gamer");

        assertEquals("{\"columns\":6}", read(new File(root, "config.json")));
        assertFalse(new File(root, "wallpaper.jpg").exists());
        assertFalse(new File(root, "wallpapers/old.jpg").exists());
        assertEquals("A", read(new File(root, "wallpapers/a.jpg")));
        assertEquals("a.jpg|A", read(new File(root, "wallpapers/captions.txt")));
        assertTrue(new File(root, "wallpapers/keep.txt").exists());
        assertFalse(new File(root, "screenshot.jpg").exists());
        assertEquals(revision, read(new File(root, "theme_revision")));
        assertEquals(revision, ThemeLibrary.currentRevision(root));
        // The library copy is untouched.
        assertEquals("{\"columns\":6}", read(new File(root, "themes/gamer/config.json")));
        String[] left = root.list((d, n) -> n.endsWith(".tmp"));
        assertEquals(0, left == null ? 0 : left.length);
    }

    @Test public void applyCarriesASingleWallpaper() throws IOException {
        File root = tmp.getRoot();
        write(new File(root, "themes/cine/config.json"), "{}");
        write(new File(root, "themes/cine/wallpaper.jpg"), "W");
        ThemeLibrary.apply(root, "cine");
        assertEquals("W", read(new File(root, "wallpaper.jpg")));
    }

    @Test public void eachApplyHasItsOwnRevision() throws IOException {
        File root = tmp.getRoot();
        write(new File(root, "themes/cine/config.json"), "{}");
        String first = ThemeLibrary.apply(root, "cine");
        String second = ThemeLibrary.apply(root, "cine");
        assertNotEquals(first, second);
        assertTrue(second.length() <= 64);
    }

    @Test public void applyRefusesWhatIsNotALibraryTheme() throws IOException {
        File root = tmp.getRoot();
        write(new File(root, "themes/nocfg/wallpaper.jpg"), "W");
        for (String name : new String[]{"nocfg", "missing", "..", null}) {
            try {
                ThemeLibrary.apply(root, name);
                fail("applied " + name);
            } catch (IOException expected) {
                // nothing written
            }
        }
        assertFalse(new File(root, "config.json").exists());
        assertFalse(new File(root, "theme_revision").exists());
    }

    @Test public void resetLeavesTheLibrary() throws IOException {
        File root = tmp.getRoot();
        write(new File(root, "themes/cine/config.json"), "{}");
        write(new File(root, "themes/cine/wallpaper.jpg"), "W");
        ThemeLibrary.apply(root, "cine");
        ThemeLibrary.reset(root);
        assertFalse(new File(root, "config.json").exists());
        assertFalse(new File(root, "wallpaper.jpg").exists());
        assertFalse(ThemeLibrary.hasActiveTheme(root));
        assertTrue(new File(root, "themes/cine/wallpaper.jpg").exists());
        assertFalse(ThemeLibrary.currentRevision(root).isEmpty());
    }

    @Test public void theActiveNameHoldsUntilAnotherThemeArrives() throws IOException {
        File root = tmp.getRoot();
        write(new File(root, "themes/cine/config.json"), "{}");
        String revision = ThemeLibrary.apply(root, "cine");
        assertEquals("cine", ThemeLibrary.activeName(root, "cine " + revision));
        // tools/theme.sh pushed another theme: new revision, so the library mark goes.
        write(new File(root, "theme_revision"), "abc123");
        assertNull(ThemeLibrary.activeName(root, "cine " + revision));
        assertNull(ThemeLibrary.activeName(root, null));
        assertNull(ThemeLibrary.activeName(root, "garbage"));
        // No active theme at all.
        new File(root, "config.json").delete();
        write(new File(root, "theme_revision"), revision);
        assertNull(ThemeLibrary.activeName(root, "cine " + revision));
    }

    @Test public void removeTakesTheWholeFolder() throws IOException {
        File root = tmp.getRoot();
        write(new File(root, "themes/cine/config.json"), "{}");
        write(new File(root, "themes/cine/wallpapers/a.jpg"), "A");
        assertTrue(ThemeLibrary.remove(root, "cine"));
        assertFalse(new File(root, "themes/cine").exists());
        assertFalse(ThemeLibrary.remove(root, ".."));
        assertTrue(new File(root, "themes").exists());
    }

    @Test public void screenshotIsFoundOnlyWhenPresent() throws IOException {
        File root = tmp.getRoot();
        write(new File(root, "themes/cine/config.json"), "{}");
        assertNull(ThemeLibrary.screenshot(root, "cine"));
        write(new File(root, "themes/cine/screenshot.jpg"), "S");
        assertEquals(new File(root, "themes/cine/screenshot.jpg"), ThemeLibrary.screenshot(root, "cine"));
        assertNull(ThemeLibrary.screenshot(root, ".."));
    }
}
