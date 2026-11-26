package com.gathin.tvhome;

import java.util.Locale;

/**
 * The Home layout, chosen in Launcher settings and independent of the wallpaper. BuzzTV opens on {@link #AURA};
 * {@link #STANDARD} is CleanYourTV's own dock and grid, kept as it was. Every other layout is a full-screen
 * {@link CustomHomeLayout} built from the {@link Buzz} design kit.
 */
final class HomeTheme {
    static final String AURA = "aura";
    /** Arena: the game-room layout (stored id kept from the earlier "Modo Juego"). */
    static final String GAMING = "gaming";
    /** Zen: the typographic layout (stored id kept from the earlier "Minimalista"). */
    static final String MINIMAL = "minimal";
    static final String BENTO = "bento";
    static final String CINEMA = "cinema";
    static final String STANDARD = "standard";

    static final String DEFAULT = AURA;
    /** In the order Launcher settings lists them. */
    static final String[] ALL = {AURA, GAMING, MINIMAL, BENTO, CINEMA, STANDARD};

    /** Warm gold focus rim & hero accent for Home Cinema. */
    static final int ACCENT_CINEMA = 0xFFF6D58E;
    /** Warm amber focus rim & bento accent for Living Room (Sala). */
    static final int ACCENT_BENTO = 0xFFF5B963;

    private HomeTheme() {}

    /** Normalizes a stored or configured theme id to one of {@link #ALL}, defaulting to {@link #DEFAULT}. */
    static String normalize(String raw) {
        if (raw == null) return DEFAULT;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (String id : ALL) {
            if (id.equals(value)) return id;
        }
        return DEFAULT;
    }

    static boolean isStandard(String id) {
        return STANDARD.equals(normalize(id));
    }

    /** True for every layout but Standard: they replace the dock and grid with a layout of their own. */
    static boolean usesCustomLayout(String id) {
        return !isStandard(id);
    }

    /** The layout's accent for shared surfaces (the All apps dialog), or 0 for Standard's white glass. */
    static int focusAccent(String id) {
        switch (normalize(id)) {
            case AURA: return Buzz.HONEY;
            case GAMING: return ArenaHomeLayout.NEON;
            case MINIMAL: return 0xFFFFFFFF;
            case CINEMA: return ACCENT_CINEMA;
            case BENTO: return ACCENT_BENTO;
            default: return 0;
        }
    }

    /** String resource for the localized display name of a theme. */
    static int labelRes(String id) {
        switch (normalize(id)) {
            case AURA: return R.string.home_theme_aura;
            case CINEMA: return R.string.home_theme_cinema;
            case BENTO: return R.string.home_theme_bento;
            case GAMING: return R.string.home_theme_gaming;
            case MINIMAL: return R.string.home_theme_minimal;
            default: return R.string.home_theme_standard;
        }
    }
}
