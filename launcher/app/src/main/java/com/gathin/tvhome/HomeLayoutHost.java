package com.gathin.tvhome;

import android.view.View;

import java.util.List;

/**
 * Bridge between {@link MainActivity} and the custom full-screen {@link CustomHomeLayout} themes
 * (Aura, Arena, Zen, Sala, Cine). Keeps wallpapers, animations, live weather and system actions shared.
 */
interface HomeLayoutHost {
    List<AppAdapter.Entry> dockApps();
    List<AppAdapter.Entry> allApps();
    void launchApp(AppAdapter.Entry app);
    void openInputs(View anchor);
    void openWifi();
    void openSettings();
    void openLauncherSettings();
    void openAllApps();
    String wifiLabel();
    /** Opens the 7-day forecast, tinted with the layout's accent (0 for BuzzTV honey). */
    void openForecast(int accent);
    /** The theme's greeting name from config ("" when none). */
    String greetingName();
}
