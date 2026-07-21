# 📡 NA7Q APRSdroid 9M2PJU Mod
### *The Ultimate APRS Companion for Android — 9M2PJU-Mod*

> 🌟 **This is the 9M2PJU-Mod** of [NA7Q's enhanced APRSdroid](https://github.com/na7q/aprsdroid),
> which is itself a fork of [Georg Lukas's original APRSdroid](https://aprsdroid.org/).
> It adds a launch splash screen, modern Android support, GitHub Actions CI/CD with
> signed release builds, and a project landing page with live download counters.

---

<div align="center">

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://aprsdroid.hamradio.my/)
[![License](https://img.shields.io/badge/License-GPLv2-blue?style=for-the-badge)](https://www.gnu.org/licenses/gpl-2.0.html)
[![Scala](https://img.shields.io/badge/Built_with-Scala-DC322F?style=for-the-badge&logo=scala&logoColor=white)](https://www.scala-lang.org/)
[![targetSdk 35](https://img.shields.io/badge/targetSdk-35_(Android_15)-CEB619?style=for-the-badge)](https://developer.android.com/about/versions/15)
[![Build](https://img.shields.io/github/actions/workflow/status/9M2PJU/APRSdroid-9M2PJU-Mod/build.yml?style=for-the-badge&label=CI)](https://github.com/9M2PJU/APRSdroid-9M2PJU-Mod/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/9M2PJU/APRSdroid-9M2PJU-Mod?style=for-the-badge&label=Latest%20Release)](https://github.com/9M2PJU/APRSdroid-9M2PJU-Mod/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/9M2PJU/APRSdroid-9M2PJU-Mod/total?style=for-the-badge&label=Total%20Downloads)](https://aprsdroid.hamradio.my/)

**[🌐 Landing Page](https://aprsdroid.hamradio.my/)** • **[⬇️ Download](https://github.com/9M2PJU/APRSdroid-9M2PJU-Mod/releases/latest)** • **[🗺️ Original APRSdroid](https://aprsdroid.org/)** • **[📦 Source](https://github.com/9M2PJU/APRSdroid-9M2PJU-Mod)**

</div>

---

## ✨ **What is NA7Q APRSdroid 9M2PJU Mod?**

NA7Q APRSdroid 9M2PJU Mod is a powerful, extensively enhanced Android client for the
[**APRS (Automatic Packet Reporting System)**](http://aprs.org/) network. It builds on
NA7Q's enhanced APRSdroid and adds modern Android compatibility, a branded splash screen,
CI/CD with signed release APKs, and a project landing page.

### 🆕 **What's new in the 9M2PJU-Mod**

This mod is a comprehensive refresh of NA7Q's enhanced APRSdroid. Below is everything that
changed compared to the upstream NA7Q fork.

#### 🏗️ **Repo & branding**
- 🔖 **Repo renamed** `NA7Q-APRSdroid` → `APRSdroid-9M2PJU-Mod` (clearer identity)
- 🏷️ **Version bumped** to `v2.0.0` (APRS tocall `APDR20`, displayed as "APRSdroid 2.0.0" in
  IGATE comment strings). Versioning is tag-driven — each release gets a `vX.Y.Z` tag.
- 🖼️ **New app icon & logo** — replaced all 6 density-specific `icon.png` files
  (ldpi/mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi) from a new 2048×2048 source. Added a 512×512
  `logo.png` for in-app branding and Play Store listing.
- 🎨 **Branded splash screen** — added `SplashTheme` that shows a full-screen splash image
  as the launcher activity's `windowBackground` during cold start. Splash image is a 153 KB
  lossy WebP (down from 4.7 MB PNG) in `drawable-nodpi/`. No activity code changes needed —
  pure theme-based splash.

#### 📱 **Modern Android support (targetSdk 33 → 35)**
- 🎯 **`targetSdkVersion` 33 → 35** (Android 15). Satisfies the Google Play targetSdk floor
  (within 1 year of latest). `compileSdkVersion` stays at 33 due to the Scala plugin's AGP
  limitation (see build notes below). `minSdkVersion` bumped 14 → 19 to enable modern
  AndroidX libraries.
- 🔐 **Foreground service types** (Android 14+ requirement): `AprsService` now declares
  `foregroundServiceType="location|microphone|connectedDevice"` and the manifest requests
  `FOREGROUND_SERVICE_LOCATION`, `FOREGROUND_SERVICE_MICROPHONE`,
  `FOREGROUND_SERVICE_CONNECTED_DEVICE`. `ServiceNotifier.start()` passes an explicit type
  bitmask to `startForeground()` on API 29+, selecting `microphone` and `connectedDevice`
  only when the corresponding runtime permission is granted.
- 📶 **Bluetooth permissions** (Android 12+): added `BLUETOOTH_SCAN` with `neverForLocation`
  and `BLUETOOTH_CONNECT`. `BLUETOOTH`/`BLUETOOTH_ADMIN` now have `maxSdkVersion="30"`.
- 📂 **Storage permissions** (Android 11+): added `MANAGE_EXTERNAL_STORAGE` for all-files
  access to offline MBTiles map files. `READ_EXTERNAL_STORAGE`/`WRITE_EXTERNAL_STORAGE` now
  have `maxSdkVersion="32"`.
- 🔔 **`POST_NOTIFICATIONS`** (Android 13+): already declared; now requested at runtime via
  `PermissionHelper` when starting the service.
- 🪟 **Edge-to-edge opt-out** (Android 15+): `UIHelper.applySystemBarInsets()` calls
  `WindowCompat.setDecorFitsSystemWindows(window, true)` to prevent content from going under
  the status/navigation bars. Applied to all list-based activities via
  `LoadingListActivity.onResume`.

#### 🎨 **UI modernization (Phase 1 — theme & chrome)**
- 🌙 **Material Design dark theme** — migrated from `Theme.Holo` (2011) to
  `Theme.MaterialComponents`. The app uses a **dark-only** theme with
  navy surfaces and amber accent, matching the app icon.
- 🎨 **Brand palette** derived from the app icon:
  - Navy surfaces `#0D182D`/`#13203A`/`#1C2F51` (elevation tints)
  - Amber accent `#CEB619`
  - Cool grey text `#9DA4B0`/`#E8EAEF`
  - Lighter navy primary `#9DB4D6` for dark-mode contrast
- 🧩 **New dependencies:** `androidx.appcompat:appcompat:1.6.1` +
  `com.google.android.material:material:1.9.0`.
- 🪟 **Status bar** now colored to match the navy primary (`colorPrimaryDark`).
- 🔘 **Material buttons** — `Widget.AppTheme.Button` style with amber background and navy
  text, applied via `materialButtonStyle`.
- 🧱 **Activity migrations:** `APRSdroid`, `ProfileImportActivity`,
  `KeyfileImportActivity` → `AppCompatActivity`. ListActivity-based and
  PreferenceActivity-based activities keep their superclasses for now (the Material theme
  applies to them via the manifest).
- 🗺️ **Map activity excluded** — `MapAct` (MapsForge) keeps `MapViewTheme` (Holo-based) for
  compatibility. Will be migrated in a later phase.

> **Phase 2 (not yet done):** ListActivity → RecyclerView, PreferenceActivity →
> PreferenceFragmentCompat, Material dialogs (`MaterialAlertDialogBuilder`), layout
> hardcoded colors → `@color/` resources, dynamic color (Material You), core-splashscreen
> API.

#### 🌐 **GitHub Pages landing page**
- 🌍 **Custom domain:** <https://aprsdroid.hamradio.my/>
- 🎨 **Dark navy + amber theme** matching the app icon.
- 📊 **Live download counters** — no backend. Real GitHub release download counts.
- ⬇️ **Download buttons** for every release APK, with file size and per-asset download count.
- 🖼️ **Splash preview**, **features grid**, **credits**.

### 🎯 **Core Features**
- 📍 **Real-time Position Reporting** — Share your location with the APRS network
- 🗺️ **Interactive Station Map** — Visualize nearby amateur radio stations with offline mapping
- 💬 **APRS Messaging** — Send and receive messages through the network
- 🔄 **Network Integration** — Full compatibility with APRS infrastructure
- 🎨 **Material Design UI** — dark-only theme with navy/amber branding

### 🚀 **Enhanced Features (inherited from NA7Q, not in official APRSdroid)**

#### 📡 **RF & Networking**
- 🔄 **Digipeater** — Direct or full digipeating capabilities
- 🌐 **2-Way IGating** — Full Internet Gateway functionality
- 📶 **Flexible Packet Routing** — Send packets via RF and APRS-IS, or RF only while IGating
- 🎚️ **Radio Control** — Support for Vero, BTech, Radioddity, and other radios
- 📻 **DigiRig Support** — Seamless integration with DigiRig interfaces
- 🔵 **Bluetooth Low Energy** — Stable BLE support

#### 🗺️ **Advanced Offline Mapping**
- 🗺️ **Offline Maps with MBTiles** — Complete offline operation capability
- 🆕 **MapsForge V3 Support** — Enhanced offline mapping with MapsForge
- 🌍 **OpenStreetMap Integration** — Full OSM compatibility for mapping
- ⚠️ **Note**: Google Maps is not supported — the mod uses OSM maps
  (MBTiles + MapsForge V3) for offline mapping, privacy, and zero dependencies

#### 📊 **Data & Compression**
- 🗜️ **Mic-E Compression** — Efficient position encoding
- 🚨 **Mic-E Emergency Status** — Including EMERGENCY status support
- 📈 **Standard Compression** — Multiple compression formats supported

#### ⚙️ **User Experience Enhancements**
- 📏 **Unit Options** — Choose between Metric or Imperial units
- 🔧 **Hardware Control** — Option to disable hardware acceleration
- 📊 **Enhanced Station Viewer** — Added speed and course information
- 💬 **Advanced Messaging Tweaks** — Features for power users
- 🆔 **Message ID Control** — Option to disable Message ID
- 📋 **Smart Hub Log** — Sort by distance or newest stations
- 🔍 **Under-the-Hood Improvements** — Numerous performance and stability enhancements

---

## 🚀 **Quick Start**

> ⚠️ **Note**: This build is a work in progress — some features may be incomplete or
> broken at the time of download, as it is actively updated.
> See the [**ChangeLog**](ChangeLog) for details.

### 📲 **Installation**

> ⚠️ **Important**: Uninstall any previous OFFICIAL version of APRSdroid before installing
> this mod — the signing key differs, so Android will refuse an in-place upgrade.

1. **Download the latest signed release APK** from the
   [**Releases page**](https://github.com/9M2PJU/APRSdroid-9M2PJU-Mod/releases/latest)
   (or browse all releases + live download counts on the
   [**landing page**](https://aprsdroid.hamradio.my/))
2. **Install** the APK on your Android device (enable "Install from unknown sources" if prompted)
3. On **Android 11+**, grant *All files access* for offline MBTiles maps (see below)

> 🗺️ **Google Maps not supported**: This mod uses OSM maps (MBTiles + MapsForge V3)
> for offline mapping, privacy, and zero dependencies. Google Maps is not available.

### 🗺️ **Setting Up Offline Maps**

For Android 11+ devices, manual storage permissions are required for offline mapping files:

1. In APRSdroid settings, go to **OSM Maps** category
2. Tap **"Grant Storage Permissions"**
3. Grant **ALL file permissions** for device storage access
4. Set map viewer to **OpenStreetMap.org** to use offline maps
5. Configure offline maps in the **OSM Maps** preferences section

If offline mode is not enabled, the online OSM maps server will be used.

#### 🗺️ **Getting Maps**

There are various providers who offer paid or free MBTiles maps. APRSdroid requires
MBTiles in **PNG or JPG format** (NOT Vector or PBF). NA7Q provides several tools for
downloading offline maps:

| Tool | Platform | Description |
|---|---|---|
| 🌍 [**World Map**](https://na7q.com/wp-content/uploads/2024/12/map.mbtiles) | Any | Ready-to-use starter world map (zoom level 6) |
| 🖥️ [**OSM Map Maker**](https://downloads.aprs.wiki/APRSdroid/gui7-concurrency.exe) | Windows | GUI tool for downloading OSM maps |
| 🐍 [**Python Map Maker**](https://na7q.com/wp-content/uploads/2025/01/gui7-concurrency.py) | Win/Mac/Linux | Python script version, cross-platform |
| 🗺️ [**Multi-Map Maker**](https://na7q.com/wp-content/uploads/2025/02/mapmaker-0.2.exe) | Windows | Supports Google, Google Sat, Google Terrain, OSM, USGS, USFS, Canada Topo, Thunderforest, MapBuilder Light/Dark, and more |
| 👁️ [**Map Viewer**](https://na7q.com/wp-content/uploads/2025/02/mapviewer.exe) | Windows | Preview different map types to see which style you prefer |
| 🌐 [**BBBike MapsForge**](https://extract.bbbike.org/) | Web | Custom MapsForge OSM maps online |

**Map tips**:
- Use a **precise location** for best results (e.g. "Portland, Oregon", "Oregon USA",
  "Texas USA"). Use the Map Viewer to double-check the selected area.
- **Zoom level** is required (1-18). A distance input is optional — if left blank for a
  city, state, or region, it will download everything within that boundary.
- For **states or larger**, zoom 13-14 is recommended. Washington State at zoom 15 is
  2-5GB depending on the map used.
- Not all maps support all zoom levels or areas — research the map type you want.

### 📚 **Documentation & Support**
- 🌐 [**9M2PJU-Mod Landing Page**](https://aprsdroid.hamradio.my/)
- 📋 [**Releases & download counters**](https://aprsdroid.hamradio.my/#download)
- 📖 [**Original APRSdroid FAQ**](https://github.com/ge0rg/aprsdroid/wiki/Frequently-Asked-Questions)
- ⚙️ [**Original APRSdroid Configuration Guide**](https://github.com/ge0rg/aprsdroid/wiki/Settings)
- 🛠️ [**NA7Q's Homepage**](https://na7q.com/aprsdroid-osm/) & [**Changelog**](https://na7q.com/aprsdroid-changelog/)

---

## 🌐 **Landing Page & Download Counters**

A static landing page is published via GitHub Pages at
**<https://aprsdroid.hamradio.my/>**. It shows:

- App branding (icon, splash, features)
- **Download buttons** for the latest release APK
- **Live download counters** — per-release and total — fetched client-side from the
  public GitHub API (`download_count` per release asset). No backend, no separate
  counter service; the numbers are real GitHub release download counts.

---

## 📜 **License**

This project is licensed under the **GNU General Public License v2.0** — see the
[LICENSE](LICENSE) file for details.

---

## 🤝 **Contributing**

Contributions are welcome from the amateur radio community! Whether you're fixing bugs,
adding features, or improving documentation, your help makes APRSdroid better for everyone.
Please open an issue or pull request at the
[**9M2PJU-Mod repo**](https://github.com/9M2PJU/APRSdroid-9M2PJU-Mod).

---

## 🙏 **Credits**

- **Georg Lukas (ge0rg)** — original [APRSdroid](https://aprsdroid.org/)
- **NA7Q** — [enhanced APRSdroid fork](https://github.com/na7q/aprsdroid) that this mod builds on
- **9M2PJU** — this mod. Contributions include:
  - 🎨 New app icon and logo across all density buckets
  - 🖼️ Branded splash screen (153 KB WebP, theme-based)
  - 📱 Modern Android support — `targetSdk 35`, foreground service types for Android 14+, Bluetooth permissions for Android 12+, storage permissions for Android 11+, `POST_NOTIFICATIONS` for Android 13+, edge-to-edge opt-out for Android 15+
  - 🎨 Material 3-inspired UI design system — `Theme.MaterialComponents` with tonal navy/amber palette, shape theming, refined typography, modern component styles (buttons, text fields, dialogs, cards, bottom sheets), dark-only
  - 🤖 GitHub Actions CI/CD — signed release APK builds, automatic GitHub Releases on `v*` tags
  - 🌐 GitHub Pages landing page at <https://aprsdroid.hamradio.my/> with live download counters
  - 🏷️ Version bumped to `v2.0.0` (tocall `APDR20`)
  - 📝 Comprehensive README rewrite documenting all changes
- **Bob Bruninga, WB4APR** — creator of APRS

APRS® is a registered trademark of Bob Bruninga, WB4APR.

---

<div align="center">

**Made with ❤️ by Amateur Radio operators, for Amateur Radio operators**

*73 and happy APRSing!* 📡

</div>
