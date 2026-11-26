package com.gathin.tvhome;

import android.view.View;

import java.util.List;

/**
 * Bridge between {@link MainActivity} (or the live preview in Ajustes) and the custom full-screen
 * {@link CustomHomeLayout} themes. Keeps wallpapers, animations, live weather and system actions shared.
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

    /** The TV's external inputs for a quick row (HDMI first), at most {@code max}; empty off a TV. */
    default List<HomeInput> inputs(int max) {
        return java.util.Collections.emptyList();
    }

    default void openInput(HomeInput input) {
    }

    /** The system's App info page for an app. */
    default void openAppInfo(AppAdapter.Entry app) {
    }

    /** The 7-day forecast for a widget, on the main thread; empty when the add-on has none. */
    default void readForecast(LiveWeather.ForecastListener out) {
        out.onForecast(java.util.Collections.emptyList());
    }

    /** One input in a layout's quick row. */
    final class HomeInput {
        final String id;
        final String label;
        final boolean connected;

        HomeInput(String id, String label, boolean connected) {
            this.id = id;
            this.label = label;
            this.connected = connected;
        }
    }
}
