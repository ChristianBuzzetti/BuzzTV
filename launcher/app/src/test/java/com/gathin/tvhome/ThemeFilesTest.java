package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeFilesTest {
    @Test public void themeFilesAreAllowed() {
        assertEquals("config.json", ThemeFiles.path(Collections.singletonList("config.json")));
        assertEquals("wallpaper.jpg", ThemeFiles.path(Collections.singletonList("wallpaper.jpg")));
        assertEquals("theme_revision", ThemeFiles.path(Collections.singletonList("theme_revision")));
    }

    @Test public void collectionImagesAreAllowed() {
        assertEquals("wallpapers/dusk.jpg", ThemeFiles.path(Arrays.asList("wallpapers", "dusk.jpg")));
        assertEquals("wallpapers/Night_2.WEBP", ThemeFiles.path(Arrays.asList("wallpapers", "Night_2.WEBP")));
    }

    @Test public void theCollectionsCaptionsAreAllowed() {
        assertEquals("wallpapers/captions.txt", ThemeFiles.path(Arrays.asList("wallpapers", "captions.txt")));
        assertNull(ThemeFiles.path(Collections.singletonList("captions.txt")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "notes.txt")));
        assertFalse(ThemeFiles.collectionImage("captions.txt"));
    }

    @Test public void anythingElseIsRefused() {
        assertNull(ThemeFiles.path(null));
        assertNull(ThemeFiles.path(Collections.emptyList()));
        assertNull(ThemeFiles.path(Collections.singletonList("cards.json")));
        assertNull(ThemeFiles.path(Collections.singletonList("..")));
        assertNull(ThemeFiles.path(Arrays.asList("..", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "..")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "run.sh")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "a b.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "../config.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", ".jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("other", "dusk.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("wallpapers", "sub", "dusk.jpg")));
    }

    @Test public void onlyTheBareFolderIsTheCollection() {
        assertTrue(ThemeFiles.isCollection(Collections.singletonList("wallpapers")));
        assertFalse(ThemeFiles.isCollection(Arrays.asList("wallpapers", "dusk.jpg")));
        assertFalse(ThemeFiles.isCollection(Collections.singletonList("config.json")));
    }

    @Test public void libraryThemeFilesAreAllowed() {
        assertEquals("themes/gamer/config.json", ThemeFiles.path(Arrays.asList("themes", "gamer", "config.json")));
        assertEquals("themes/gamer/wallpaper.jpg", ThemeFiles.path(Arrays.asList("themes", "gamer", "wallpaper.jpg")));
        assertEquals("themes/gamer/screenshot.jpg", ThemeFiles.path(Arrays.asList("themes", "gamer", "screenshot.jpg")));
        assertEquals("themes/istanbul_v2/wallpapers/night.jpg",
                ThemeFiles.path(Arrays.asList("themes", "istanbul_v2", "wallpapers", "night.jpg")));
        assertEquals("themes/gamer/wallpapers/captions.txt",
                ThemeFiles.path(Arrays.asList("themes", "gamer", "wallpapers", "captions.txt")));
    }

    @Test public void libraryPathsOutsideTheLayoutAreRefused() {
        assertNull(ThemeFiles.path(Arrays.asList("themes", "Gamer", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "..", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "gamer", "theme_revision")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "gamer", "run.sh")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "gamer", "wallpapers", "..")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "gamer", "wallpapers", "a", "b.jpg")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "gamer", "themes", "x", "config.json")));
        assertNull(ThemeFiles.path(Arrays.asList("themes", "gamer")));
    }

    @Test public void aLibraryThemeIsDeletedOnlyByItsBareFolder() {
        assertEquals("gamer", ThemeFiles.libraryTheme(Arrays.asList("themes", "gamer")));
        assertNull(ThemeFiles.libraryTheme(Collections.singletonList("themes")));
        assertNull(ThemeFiles.libraryTheme(Arrays.asList("themes", "..")));
        assertNull(ThemeFiles.libraryTheme(Arrays.asList("themes", "gamer", "config.json")));
        assertNull(ThemeFiles.libraryTheme(null));
    }
}
