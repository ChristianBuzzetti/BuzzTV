package com.gathin.tvhome;

import java.util.Locale;

/**
 * Home screen layout themes, independent of the chosen wallpaper/background.
 * Default is {@link #STANDARD}, which leaves the built-in layout untouched.
 */
final class HomeTheme {
    static final String STANDARD = "standard";
    static final String CINEMA = "cinema";
    static final String BENTO = "bento";
    static final String GAMING = "gaming";
    static final String MINIMAL = "minimal";

    static final String[] ALL = {STANDARD, CINEMA, BENTO, GAMING, MINIMAL};

    /** Warm gold focus rim & hero accent for Home Cinema. */
    static final int ACCENT_CINEMA = 0xFFF6D58E;
    /** Warm amber focus rim & bento accent for Living Room (Sala). */
    static final int ACCENT_BENTO = 0xFFF5B963;
    /** Crisp emerald-green HUD focus rim for Game Mode. */
    static final int ACCENT_GAMING = 0xFF4ADE80;

    private HomeTheme() {}

    /** Normalizes a stored or configured theme id to one of {@link #ALL}, defaulting to {@link #STANDARD}. */
    static String normalize(String raw) {
        if (raw == null) return STANDARD;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (String id : ALL) {
            if (id.equals(value)) return id;
        }
        return STANDARD;
    }

    static boolean isStandard(String id) {
        return STANDARD.equals(normalize(id));
    }

    /** True for themes that replace the standard dock+grid view with a dedicated architectural layout. */
    static boolean usesCustomLayout(String id) {
        String n = normalize(id);
        return CINEMA.equals(n) || BENTO.equals(n) || GAMING.equals(n);
    }

    /** Focus highlight accent ARGB for GlassView, or 0 for default white glass. */
    static int focusAccent(String id) {
        switch (normalize(id)) {
            case CINEMA: return ACCENT_CINEMA;
            case BENTO: return ACCENT_BENTO;
            case GAMING: return ACCENT_GAMING;
            default: return 0;
        }
    }

    /** Glass highlight style override for this theme, or {@code fallback} for Standard. */
    static String glassStyle(String id, String fallback) {
        switch (normalize(id)) {
            case CINEMA:
            case BENTO:
            case MINIMAL:
                return Config.GLASS_HALO;
            case GAMING:
                return Config.GLASS_RIM;
            default:
                return fallback;
        }
    }

    /** Multiplier applied to the focus highlight corner radius (smaller = sharper corners). */
    static float focusCornerScale(String id) {
        switch (normalize(id)) {
            case GAMING: return 0.45f;
            case CINEMA: return 0.75f;
            case BENTO: return 0.85f;
            case MINIMAL: return 0.90f;
            default: return 1.0f;
        }
    }

    /** Primary clock text size in sp. */
    static float clockSizeSp(String id) {
        switch (normalize(id)) {
            case BENTO: return 68f;
            case MINIMAL: return 66f;
            case CINEMA: return 32f;
            case GAMING: return 28f;
            default: return 52f;
        }
    }

    /** Top-bar pill background fill color, or 0 to keep the default drawable. */
    static int pillBgColor(String id) {
        switch (normalize(id)) {
            case CINEMA: return 0x80121824;
            case BENTO: return 0x991A1612;
            case GAMING: return 0xCC0D1114;
            case MINIMAL: return 0x40000000;
            default: return 0;
        }
    }

    /** Top-bar pill border color when unfocused, or 0 to keep the default drawable. */
    static int pillStrokeColor(String id) {
        switch (normalize(id)) {
            case CINEMA: return 0x4DF6D58E;
            case BENTO: return 0x52F5B963;
            case GAMING: return 0x664ADE80;
            case MINIMAL: return 0x3DFFFFFF;
            default: return 0;
        }
    }

    /** Top-bar pill corner radius in dp. */
    static float pillCornerDp(String id) {
        switch (normalize(id)) {
            case GAMING: return 6f;
            case CINEMA: return 12f;
            case BENTO: return 14f;
            case MINIMAL: return 22f;
            default: return 20f;
        }
    }

    /** Whether the dock background band should be drawn for this theme. */
    static boolean dockBand(String id, boolean fallback) {
        return MINIMAL.equals(normalize(id)) ? false : fallback;
    }

    /** Effective dock opacity for this theme. */
    static int dockOpacity(String id, int fallback) {
        switch (normalize(id)) {
            case GAMING: return Math.max(fallback, 88);
            case CINEMA: return Math.min(fallback, 78);
            case BENTO: return Math.min(fallback, 80);
            default: return fallback;
        }
    }

    /** Whether top-bar pills collapse to compact icon-only buttons for this theme. */
    static boolean iconOnlyTopBar(String id, boolean fallback) {
        return MINIMAL.equals(normalize(id)) || fallback;
    }

    /** String resource for the localized display name of a theme. */
    static int labelRes(String id) {
        switch (normalize(id)) {
            case CINEMA: return R.string.home_theme_cinema;
            case BENTO: return R.string.home_theme_bento;
            case GAMING: return R.string.home_theme_gaming;
            case MINIMAL: return R.string.home_theme_minimal;
            default: return R.string.home_theme_standard;
        }
    }
}
