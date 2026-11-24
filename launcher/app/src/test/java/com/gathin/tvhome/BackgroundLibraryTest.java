package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BackgroundLibraryTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File f, String text) throws IOException {
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private File library() throws IOException {
        File root = tmp.getRoot();
        write(new File(root, "themes/christmas/config.json"), "{}");
        write(new File(root, "themes/christmas/wallpapers/b.jpg"), "b");
        write(new File(root, "themes/christmas/wallpapers/a.jpg"), "a");
        write(new File(root, "themes/christmas/wallpapers/captions.txt"), "a.jpg Sleigh");
        write(new File(root, "themes/gamer/config.json"), "{}");
        write(new File(root, "themes/gamer/wallpaper.jpg"), "g");
        write(new File(root, "themes/minimal/config.json"), "{}");          // no picture: not a section
        write(new File(root, "themes/minimal/screenshot.jpg"), "s");
        write(new File(root, "themes/Bad/wallpaper.jpg"), "x");             // not a valid name
        return root;
    }

    private static List<String> names(List<File> files) {
        String[] out = new String[files.size()];
        for (int i = 0; i < out.length; i++) out[i] = files.get(i).getParentFile().getName() + "/" + files.get(i).getName();
        return Arrays.asList(out);
    }

    @Test public void sectionsAreFoldersWithAPicture() throws IOException {
        assertEquals(Arrays.asList("christmas", "gamer"), BackgroundLibrary.sections(library()));
        assertTrue(BackgroundLibrary.sections(null).isEmpty());
    }

    @Test public void aSectionListsItsSinglePictureThenItsCollection() throws IOException {
        File root = library();
        write(new File(root, "themes/christmas/wallpaper.jpg"), "w");
        assertEquals(Arrays.asList("christmas/wallpaper.jpg", "wallpapers/a.jpg", "wallpapers/b.jpg"),
                names(BackgroundLibrary.images(root, "christmas")));
        assertTrue(BackgroundLibrary.images(root, "..").isEmpty());
    }

    @Test public void choicesResolveToTheirPictures() throws IOException {
        File root = library();
        assertEquals(Arrays.asList("wallpapers/a.jpg", "wallpapers/b.jpg", "gamer/wallpaper.jpg"),
                names(BackgroundLibrary.resolve(root, BackgroundLibrary.MODE_ALL, null, null)));
        assertEquals(Arrays.asList("wallpapers/a.jpg", "wallpapers/b.jpg"),
                names(BackgroundLibrary.resolve(root, BackgroundLibrary.MODE_SECTION, "christmas", null)));
        assertEquals(Collections.singletonList("wallpapers/b.jpg"),
                names(BackgroundLibrary.resolve(root, BackgroundLibrary.MODE_IMAGE, "christmas", "b.jpg")));
    }

    @Test public void aChoiceTheLibraryNoLongerHasFallsBackToTheTheme() throws IOException {
        File root = library();
        assertTrue(BackgroundLibrary.resolve(root, null, null, null).isEmpty());
        assertTrue(BackgroundLibrary.resolve(root, BackgroundLibrary.MODE_IMAGE, "christmas", "gone.jpg").isEmpty());
        assertTrue(BackgroundLibrary.resolve(root, BackgroundLibrary.MODE_SECTION, "removed", null).isEmpty());
        assertTrue(BackgroundLibrary.resolve(root, "none", "christmas", null).isEmpty());
    }

    @Test public void onlyAChosenSectionLendsItsSettings() {
        assertEquals("cine", BackgroundLibrary.settingsSection(BackgroundLibrary.MODE_SECTION, "cine"));
        assertEquals("cine", BackgroundLibrary.settingsSection(BackgroundLibrary.MODE_IMAGE, "cine"));
        assertNull(BackgroundLibrary.settingsSection(BackgroundLibrary.MODE_ALL, "cine"));
    }

    @Test public void picturesAreNamedByCaptionOrFile() {
        assertEquals("Sleigh", BackgroundLibrary.imageLabel(new File("a.jpg"), " Sleigh "));
        assertEquals("Sleigh over a snowy night", BackgroundLibrary.imageLabel(new File("a.jpg"),
                "Sleigh over a snowy night · CleanYourTV, CC0"));
        assertEquals("Snowy night", BackgroundLibrary.imageLabel(new File("snowy-night.jpg"), null));
        assertEquals("Snowy night", BackgroundLibrary.imageLabel(new File("snowy-night.jpg"), ""));
    }

    @Test public void everyBackgroundMovesOnceTheSwitchIsOn() {
        assertEquals(0, Config.wallpaperDrift(false, 4));
        assertEquals(0, Config.wallpaperDrift(false, 0));
        assertEquals(4, Config.wallpaperDrift(true, 4));
        assertEquals(Config.DEFAULT_DRIFT, Config.wallpaperDrift(true, 0));
        assertEquals(WallpaperMotion.NONE, Config.wallpaperEffect(false, "rain"));
        assertEquals("rain", Config.wallpaperEffect(true, "rain"));
        assertEquals(WallpaperMotion.MOTES, Config.wallpaperEffect(true, WallpaperMotion.NONE));
        assertEquals(WallpaperMotion.MOTES, Config.wallpaperEffect(true, ""));
    }

    @Test public void rowsUnderTheDockHideOnlyWhileAsked() {
        assertTrue(GridNavigation.veiled(true, 6, 6));
        assertFalse(GridNavigation.veiled(true, 5, 6));
        assertFalse(GridNavigation.veiled(false, 6, 6));
    }

    @Test public void wallpaperAnimationFallsBackToLegacySwitchAndResolvesAllModes() {
        assertEquals(Config.WALLPAPER_ANIMATION_NONE, Config.wallpaperAnimation(null, false));
        assertEquals(Config.WALLPAPER_ANIMATION_AUTO, Config.wallpaperAnimation(null, true));
        assertEquals(Config.WALLPAPER_ANIMATION_WEATHER, Config.wallpaperAnimation("weather", false));
        assertEquals(WallpaperMotion.FIREFLIES, Config.wallpaperAnimation(" FIREFLIES ", false));
        assertEquals(WallpaperMotion.STORM, Config.wallpaperAnimation("storm", false));
        assertEquals(Config.WALLPAPER_ANIMATION_NONE, Config.wallpaperAnimation("bogus", true));

        assertFalse(Config.wallpaperAnimationEnabled(Config.WALLPAPER_ANIMATION_NONE));
        assertTrue(Config.wallpaperAnimationEnabled(Config.WALLPAPER_ANIMATION_WEATHER));
        assertTrue(Config.wallpaperAnimationEnabled(WallpaperMotion.SUNBEAMS));

        assertEquals(WallpaperMotion.NONE, Config.wallpaperEffectForAnimation(Config.WALLPAPER_ANIMATION_NONE, "rain"));
        assertEquals(WallpaperMotion.RAIN, Config.wallpaperEffectForAnimation(Config.WALLPAPER_ANIMATION_AUTO, "rain"));
        assertEquals(WallpaperMotion.MOTES, Config.wallpaperEffectForAnimation(Config.WALLPAPER_ANIMATION_AUTO, ""));
        assertEquals(WallpaperMotion.FIREFLIES, Config.wallpaperEffectForAnimation(WallpaperMotion.FIREFLIES, "rain"));
    }

    @Test public void rotationIntervalOverrideRespectsBounds() {
        assertEquals(20, Config.chosenRotationMinutes(0, 20));
        assertEquals(20, Config.chosenRotationMinutes(-5, 20));
        assertEquals(5, Config.chosenRotationMinutes(5, 20));
        assertEquals(60, Config.chosenRotationMinutes(60, 20));
        assertEquals(20, Config.chosenRotationMinutes(9999, 20));
    }
}
