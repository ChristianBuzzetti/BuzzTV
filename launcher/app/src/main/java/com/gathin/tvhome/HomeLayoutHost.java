package com.gathin.tvhome;

import android.view.View;

import java.util.List;

/**
 * Bridge between {@link MainActivity} and custom full-screen {@link CustomHomeLayout} themes
 * (Gaming, Bento/Sala, Cinema). Keeps wallpapers, animations, live weather, and system actions shared.
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
}
