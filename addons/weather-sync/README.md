# BuzzTV Weather Sync

A tiny, headless companion add-on for BuzzTV that automatically detects the TV's approximate city via HTTPS IP geolocation (`get.geojs.io` / `ipwho.is`) and fetches current weather from [Open-Meteo](https://open-meteo.com/) (free, no API key).

It exposes a single read-only `ContentProvider` (`content://com.buzztv.weathersync/current`) queried by the BuzzTV launcher only when **Live weather** is enabled in Launcher settings. No dock icon, no background service, no polling.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Automatic IP city detection (`get.geojs.io` / `ipwho.is`) and current weather from Open-Meteo. |

No location permission, no account, no telemetry, no background service.
