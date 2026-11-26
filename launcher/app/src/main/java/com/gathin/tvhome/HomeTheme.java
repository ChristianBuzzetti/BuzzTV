package com.gathin.tvhome;

import java.util.Locale;

/**
 * The Home layout, chosen in Ajustes › Diseño de Inicio and independent of the wallpaper. BuzzTV opens on
 * {@link #MARQUEE}; {@link #STANDARD} ("Clásico") is CleanYourTV's own dock and grid, kept as it was. Every
 * other layout is a full-screen {@link CustomHomeLayout} built from the {@link Buzz} design kit.
 */
final class HomeTheme {
    static final String MARQUEE = "marquee";
    static final String AURA = "aura";
    static final String PANEL = "panel";
    static final String ORBIT = "orbit";
    static final String MOSAIC = "mosaic";
    static final String BENTO = "bento";
    static final String STANDARD = "standard";
    /** Earlier BuzzTV layouts: no longer offered, still honoured when a TV has one stored. */
    static final String GAMING = "gaming";
    static final String MINIMAL = "minimal";
    static final String CINEMA = "cinema";

    static final String DEFAULT = MARQUEE;
    /** The layouts the settings carousel offers, in its order. */
    static final String[] ALL = {MARQUEE, AURA, PANEL, ORBIT, MOSAIC, BENTO, STANDARD};
    private static final String[] KNOWN = {MARQUEE, AURA, PANEL, ORBIT, MOSAIC, BENTO, STANDARD, GAMING, MINIMAL, CINEMA};

    /** Warm gold focus rim & hero accent for Home Cinema. */
    static final int ACCENT_CINEMA = 0xFFF6D58E;
    /** Warm amber focus rim & bento accent for Living Room (Sala). */
    static final int ACCENT_BENTO = 0xFFF5B963;

    private HomeTheme() {}

    /** Normalizes a stored or configured theme id to a known one, defaulting to {@link #DEFAULT}. */
    static String normalize(String raw) {
        if (raw == null) return DEFAULT;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (String id : KNOWN) {
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
            case GAMING: return ArenaHomeLayout.NEON;
            case MINIMAL: return 0xFFFFFFFF;
            case CINEMA: return ACCENT_CINEMA;
            case BENTO: return ACCENT_BENTO;
            case STANDARD: return 0;
            default: return Buzz.HONEY;
        }
    }

    /** Position in the carousel, 0-based, or -1 for a layout no longer offered. */
    static int index(String id) {
        String n = normalize(id);
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(n)) return i;
        return -1;
    }

    /** String resource for the localized display name of a theme. */
    static int labelRes(String id) {
        switch (normalize(id)) {
            case MARQUEE: return R.string.home_theme_marquee;
            case AURA: return R.string.home_theme_aura;
            case PANEL: return R.string.home_theme_panel;
            case ORBIT: return R.string.home_theme_orbit;
            case MOSAIC: return R.string.home_theme_mosaic;
            case CINEMA: return R.string.home_theme_cinema;
            case BENTO: return R.string.home_theme_bento;
            case GAMING: return R.string.home_theme_gaming;
            case MINIMAL: return R.string.home_theme_minimal;
            default: return R.string.home_theme_standard;
        }
    }

    /** One line on what the layout is, for the settings screen. */
    static int descriptionRes(String id) {
        switch (normalize(id)) {
            case MARQUEE: return R.string.home_theme_marquee_desc;
            case AURA: return R.string.home_theme_aura_desc;
            case PANEL: return R.string.home_theme_panel_desc;
            case ORBIT: return R.string.home_theme_orbit_desc;
            case MOSAIC: return R.string.home_theme_mosaic_desc;
            case BENTO: return R.string.home_theme_bento_desc;
            case STANDARD: return R.string.home_theme_standard_desc;
            default: return R.string.home_theme_legacy_desc;
        }
    }
}
