# OmniTAK Radio

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

OmniTAK Radio is a work-in-progress fork of [OmniTAK-Android](https://github.com/engindearing-projects/OmniTAK-Android) for a mapless, button-operated TAK communications client on rugged Android devices. The first test device is the Sonim **XP5900 (XP5plus) with knobs**; earlier XP5 models are distinct and remain unverified.

**Current state:** The fork and independent app identity are established, but the upstream map UI/dependencies are **still present**. There is no radio UI, voice, hardware PTT integration or verified screen-off knob support yet. Do not deploy it as a field radio. See [the upstream technical assessment](docs/OMNITAK_RADIO_ASSESSMENT.md) for the migration boundaries and acceptance gates.

The fork retains the upstream Kotlin/Jetpack Compose code and Apache-2.0 attribution. TAK connectivity, CoT, GeoChat, certificates and position reporting are the components to preserve as the map stack is removed.

## Upstream capabilities currently inherited (not the intended radio feature set)

- **TAK Server connectivity**: TCP / TLS / mTLS with client-certificate enrollment
- **Cursor-on-Target**: full CoT XML parser, marker rendering, server messaging
- **Tactical map**: MapLibre Native Android with custom layers (contacts, drawing, aircraft, mesh nodes, grid, measurement)
- **ADS-B traffic**: aircraft overlay with bring-your-own provider
- **Meshtastic**: TCP connection to Meshtastic mesh radios
- **Drawing tools**: points, lines, polygons, range/bearing, measurement
- **Multi-server management**: connect to multiple TAK servers
- **Radial menu**: quick actions on map long-press
- **Material 3 dark theme**: tactical color palette

## Requirements

- Android 8.0 (API 26) or later
- Android Studio Ladybug or later (for development)
- JDK 17 (CI) or a compatible JDK 21 (local builds verified); the system-default Java 26 is not supported by this Gradle setup
- A TAK Server you can reach (BYO, see above)

## Getting started

```bash
git clone https://github.com/ivan-lambda-consulting/omnitak-radio.git
cd omnitak-radio
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

If your system Java is 26 but Android Studio's SDK and a JDK 21 are installed locally, point this command at them without changing repository configuration:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ANDROID_HOME="$HOME/Android/Sdk" \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

To install on a connected device:

```bash
./gradlew installDebug
```

Or open the project in Android Studio and run normally, debug builds work out-of-the-box without any signing key configuration.

### Release builds (your own signing key)

To produce a release APK signed with your own upload key:

1. Generate an upload keystore:
   ```bash
   keytool -genkey -v -keystore my-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
   ```
2. Copy `keystore.properties.example` to `keystore.properties` (gitignored) and fill in your values
3. Build:
   ```bash
   ./gradlew assembleRelease
   ```

If `keystore.properties` is absent, release builds gracefully fall back to the debug signing config so the project always builds.

## Architecture

```
app/src/main/kotlin/soy/engindearing/omnitak/mobile/
├── data/            # Models + persistence (TAKServer, CoTEvent, ChatMessage, etc.)
├── domain/          # State stores (ServerManager, ChatStore, ContactStore, etc.)
└── ui/
    ├── screens/     # Top-level screens (Map, Servers, Chat, Meshtastic, Settings, etc.)
    ├── components/  # Reusable layers and widgets (TacticalMap, RadialMenu, etc.)
    ├── navigation/  # Compose Navigation graph
    └── theme/       # Material 3 theme + tactical colors
```

The app is pure Kotlin + Compose with no native bridge. A future release will integrate the shared OmniTAK Rust core via JNI. Its source is being prepared for separate open-source release as **OmniTAK-Core**.

### Plugins

OmniTAK ships a compile-time plugin SDK (`:plugins:plugin-sdk`) and a reference
plugin (`:plugins:example-adsb`, the ADS-B aircraft overlay). Plugins are
statically-linked Gradle modules, no dynamic/remote code, so the app stays
Play-Store compliant. See **[docs/PLUGIN_AUTHORING.md](docs/PLUGIN_AUTHORING.md)**
for the contract, the host seams (map overlay / radial / CoT / settings), and a
step-by-step "add a plugin" guide. Plugin authoring mirrors the iOS SDK so a
plugin ports across platforms in about a day.

```
plugins/
├── plugin-sdk/      # OmniTAKPlugin, PluginHost, PluginRegistry, value types
└── example-adsb/    # ADS-B reference plugin (OpenSky overlay + settings)
```

## Permissions

| Permission | Why |
|------------|-----|
| `INTERNET` | TAK Server connectivity |
| `ACCESS_NETWORK_STATE` | Detect connectivity changes |
| `ACCESS_FINE_LOCATION` | Self-location reporting (PPLI), GPS-aware tools |
| `ACCESS_COARSE_LOCATION` | Fallback for users who deny precise location |

No tracking, no analytics, no third-party SDKs.

## Security & privacy

- All TAK Server connections are TLS 1.2+ by default
- Client certificates are stored in Android Keystore
- No outbound traffic except to user-configured TAK Servers and ADS-B providers

Found a vulnerability? See [SECURITY.md](SECURITY.md) for responsible disclosure.

## Contributing

Contributions welcome. See [CONTRIBUTING.md](CONTRIBUTING.md). For larger changes, please open an issue first.

## License

Apache License 2.0. See [LICENSE](LICENSE).

**Free to use, modify, and share, with no separate permission needed.** Apache 2.0 covers personal, volunteer, commercial, and government use at no cost. It only asks that you keep the existing copyright and license notice in place, and note any changes you make to the source. Translations and other contributions are welcome.

OmniTAK-Android uses the following open-source components:

- [MapLibre Native Android](https://github.com/maplibre/maplibre-native): BSD 2-Clause
- [AndroidX](https://developer.android.com/jetpack/androidx): Apache 2.0
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization): Apache 2.0
- [Jetpack Compose](https://developer.android.com/jetpack/compose): Apache 2.0
- [Unishox2](https://github.com/siara-cc/Unishox2) (siara-cc): Apache 2.0, pure-Kotlin port for Meshtastic TAKPacket string compression

## Acknowledgments

Built by [Engindearing](https://engindearing.soy). Inspired by [ATAK](https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV), iTAK, [OpenTAKServer](https://github.com/brian7704/OpenTAKServer), and the broader TAK community.

The companion iOS client is [OmniTAK-iOS](https://github.com/engindearing-projects/OmniTAK-iOS).

OmniTAK is not affiliated with or endorsed by the U.S. Department of Defense, the TAK Product Center, or any other organization.
