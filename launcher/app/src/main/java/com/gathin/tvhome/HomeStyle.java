package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The per-TV look options from Ajustes › Diseño de Inicio, shared by every BuzzTV layout: a fixed accent or the
 * focused app's own colour, comfortable or compact density, the greeting, and the inputs row. Kept in the
 * launcher's own preferences (Config.SETTINGS_NAME), apart from any theme.
 */
final class HomeStyle {
    static final String ACCENT_KEY = "home_accent";
    static final String COMPACT_KEY = "home_compact";
    static final String GREETING_KEY = "home_greeting";
    static final String INPUTS_KEY = "home_inputs_row";
    /** 0 = the focused app's colour; then the fixed choices offered in settings. */
    static final int[] ACCENTS = {0, Buzz.HONEY, 0xFF5AB0FF, 0xFF3DD68C, 0xFFF7F8FA};

    final int accent;
    final boolean compact;
    final boolean greeting;
    final boolean inputsRow;

    HomeStyle(int accent, boolean compact, boolean greeting, boolean inputsRow) {
        this.accent = accent;
        this.compact = compact;
        this.greeting = greeting;
        this.inputsRow = inputsRow;
    }

    static HomeStyle load(Context c) {
        SharedPreferences p = c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        return new HomeStyle(validAccent(p.getInt(ACCENT_KEY, 0)), p.getBoolean(COMPACT_KEY, false),
                p.getBoolean(GREETING_KEY, true), p.getBoolean(INPUTS_KEY, true));
    }

    void save(Context c) {
        c.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(ACCENT_KEY, accent).putBoolean(COMPACT_KEY, compact)
                .putBoolean(GREETING_KEY, greeting).putBoolean(INPUTS_KEY, inputsRow).apply();
    }

    /** Only one of the offered colours; anything else (an old or hand-edited value) means automatic. */
    static int validAccent(int stored) {
        for (int a : ACCENTS) if (a == stored) return a;
        return 0;
    }

    /** The accent for an app: the fixed one when chosen, else the app's own readable colour. */
    int accentFor(AppAdapter.Entry e) {
        return accent != 0 ? accent : Buzz.accentOf(e);
    }

    /** The accent where no app is in focus. */
    int baseAccent() {
        return accent != 0 ? accent : Buzz.HONEY;
    }

    /** Sizes multiply by this: compact fits more on screen. */
    float scale() {
        return compact ? 0.86f : 1f;
    }

    HomeStyle with(int accent, boolean compact, boolean greeting, boolean inputsRow) {
        return new HomeStyle(validAccent(accent), compact, greeting, inputsRow);
    }
}
