package com.gathin.tvhome;

import android.view.View;

/**
 * Contract for architectural Home screen themes that replace the standard dock+grid layout
 * while floating over {@link WallpaperPlayer}'s live wallpaper and particle effects.
 */
interface CustomHomeLayout {
    View rootView();
    void bind(HomeLayoutHost host);
    void onAppsChanged();
    void onWeather(LiveWeather.Reading reading);
    void onWifiChanged(String label);
    boolean handleBack();

    /** Home came back to the front: refresh anything that depends on the time of day (the greeting). */
    default void onShown() {
    }
}
