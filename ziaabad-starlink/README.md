# Ziaabad Starlink

Android WebView package for the supplied WIFI RADAR PRO HTML interface.

## Build
The GitHub Actions workflow builds a debug APK on every push affecting this folder and publishes it as an Actions artifact and as the `android-latest` prerelease asset.

## Important capability note
The supplied HTML currently contains sample/mock Wi-Fi networks and simulated scan updates. This APK packages that same interface; it does **not** perform real nearby Wi-Fi discovery, identify an ISP/operator, determine a router's password, or prove that a network is Starlink. Those features require a separately implemented Android-native scanner and lawful OS permissions. Starlink classification based on SSID/BSSID is only a heuristic and cannot confirm satellite service.
