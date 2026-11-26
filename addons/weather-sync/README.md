# BuzzTV Weather Sync

A tiny, headless companion add-on for BuzzTV that automatically detects the TV's approximate city via HTTPS IP geolocation (`get.geojs.io` / `ipwho.is`) and fetches current weather and a 7-day forecast from [Open-Meteo](https://open-meteo.com/) (free, no API key) in a single request every 30 minutes at most.

It exposes a read-only `ContentProvider`: `content://com.buzztv.weathersync/current` (one row: temperature, condition, day/night, city, and from 1.1 feels-like, humidity and wind) and, from 1.1, `content://com.buzztv.weathersync/forecast` (one row a day: date, condition, high, low, chance of rain), queried by the BuzzTV launcher only when **Live weather** is enabled in Launcher settings. No dock icon, no background service, no polling.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Automatic IP city detection (`get.geojs.io` / `ipwho.is`) and current weather from Open-Meteo. |

No location permission, no account, no telemetry, no background service.
