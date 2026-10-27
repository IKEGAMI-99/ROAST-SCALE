# ROAST SCALE 💀

Xiaomi XMTZC02HM body-composition journal with a local Gemma 4 E4B / LiteRT-LM sarcastic coach.

**Status:** Source code is present. Physical BLE hardware, GPU inference and signed release builds still require validation. No APK release is assumed to exist until the release workflow succeeds.

## Features

- Passive BLE scan of XMTZC02HM (0x181B / 13 byte service data) without pairing or Xiaomi login.
- Only stabilized weight readings are recorded, with impedance if available, deduplicating repeated BLE advertisements.
- Estimates BMI, fat, water, muscle, bone, protein, visceral fat index and basal metabolic rate using reverse-engineered old Mi Fit formulas.
- SQLite on-device record of date, weight, estimates and AI comments.
- Compose dark dashboard, mini weight graph, history, profile and adjustable roast level.
- Model download on demand from Hugging Face (~3.66 GB), with partial download resume, local inference through LiteRT-LM 0.18.0.
- GitHub Releases in-app update detection, SHA256 verification, and Android system installer.

## Build and usage

Recent Android Studio, JDK 17, Gradle 8.14.3, Android SDK 36:

    gradle :app:testDebugUnitTest :app:assembleDebug

Every push also triggers Android CI; the workflow includes a debug APK artifact. Debug builds have ephemeral signing and cannot replace an installed APK signed differently.

On first run:
1. Enter your height, age and the sex to use for estimates under Settings.
2. Download Gemma E4B in Settings (Wi-Fi and >3.7 GB spare storage recommended).
3. Enable Bluetooth, allow Nearby Devices, tap BLE measurement, and step barefoot on the scale.
4. On a stable reading, the app stores the measurement and generates a roast when E4B is ready.

Only foreground scanning is implemented. There is no always-on background scanner.

## Signed Releases and in-app updates

GitHub Actions builds an APK on each push to `main` and publishes a newer
`vMAJOR.MINOR.PATCH` GitHub Release **only if the four signing secrets below exist**.
You can also run the workflow from Actions → Android CI and GitHub Releases →
Run workflow (main). No manual git tag is required; release assets are
`roast-scale.apk` and `roast-scale.apk.sha256`.

The private signing key **must not be embedded in the APK or committed to this
public source repository**. Android only requires the APK *signature* to remain
the same across versions. Set these repository **Actions secrets**:

1. `SIGNING_KEYSTORE_BASE64`: base64 encoded PKCS12 or JKS file contents.
2. `SIGNING_STORE_PASSWORD`: keystore password.
3. `SIGNING_KEY_PASSWORD`: signing key password.
4. `SIGNING_KEY_ALIAS`: key alias.

Configure at https://github.com/IKEGAMI-99/ROAST-SCALE/settings/secrets/actions
The signed-release job will print a clear error if any secret is missing.

Generate once with keytool if needed:

    keytool -genkeypair -noprompt -storetype PKCS12 -keystore roast-release.p12 -alias roast -keyalg RSA -keysize 3072 -validity 11000 -dname "CN=ROAST SCALE,C=JP"

Then encode:

    base64 -w 0 roast-release.p12   # Linux
    base64 < roast-release.p12 | tr -d '\n'  # macOS

Back up the keystore. Never replace the keystore on later releases. Once
GitHub Releases contains the first signed APK, install that official build.
**Existing Actions debug builds cannot be upgraded in place**: Android
requires a matching signing certificate, so uninstall the old debug APK first
(app data will be erased). Starting with the *signed release* version,
all later versions from this workflow use the same key and can be installed
through the in-app Settings → Update control.

The updater reads the public latest release, verifies APK SHA-256, checks the
package name and version, then uses the Android package installer. Android
performs signature validation and asks for installation approval. The update
may also require enabling "Allow from this source" for the app.

**Security note:** A short-lived personal test signing keystore was briefly
committed during setup and then removed; do not use or trust that exposed test
key. Use a freshly generated private signing identity via GitHub Actions
Secrets, as described above.

## Model and metrics

E4B .litertlm: https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
BLE protocol: https://github.com/freenetwork/ble-in-xiaomi/blob/master/doc/devices/huami.health.scale2.md
BMI/BIA formulas: https://github.com/ipanalytics/Xiaomi-Scale-Unlocked-Reverse-Engineering
LiteRT-LM Android API: https://developer.android.com/agents/skills/device-ai/litert/litert-lm/skill

Only weight and impedance are actual data from this scale. Fat, muscle, etc. are estimates based on 2017 reverse-engineered regressions; they may differ from Xiaomi's current app. This app is not a medical device and should not be used for diagnosis.

## Privacy

No scale data, profile information or prompt is transmitted to a server. Only the AI model and app updates are downloaded over the network. The SQLite database and model live in private app storage; uninstalling the app erases them. Matching-signature in-place updates retain them.

## Next verification steps

- Test on XMTZC02HM hardware, confirm impedance broadcasts and deduplication.
- Run E4B LiteRT-LM on the target POCO device and verify GPU/CPU fallback.
- Configure fixed signing and publish first signed release.


## BLE troubleshooting and diagnostics

Open the **測定** tab and tap **BLE測定を開始**. Version after the first prototype shows
three counters: nearby BLE advertisements, Xiaomi 0x181B packets, and valid measurements.
There is also the last raw Xiaomi HEX payload, decode status, and recently seen BLE device names.

- **0 nearby BLE packets**: check Android nearby devices permission, system Bluetooth switch,
  and (on some phones) the location-services toggle. Keep the app foregrounded.
- **Nearby BLE packets but 0 Xiaomi packets**: bring the scale within 1 m, step on it,
  wait for a stabilized display, and temporarily close competing Xiaomi or Zepp Life apps.
- **Xiaomi packets but 0 valid measurements**: share the raw HEX and the displayed
  decode status to adjust the parser for the device firmware.
- A final **0xA6** packet is now accepted: it is stabilized measurement data even
  if the flag "person stepped off" is set.

No user profile is needed to test reception. A profile is only used for the estimated
body-composition metrics.

Note that a new unsigned debug APK from GitHub Actions usually has a different Android
debug signature; back up existing data and uninstall the earlier debug APK if Android
rejects the new APK as an incompatible update.


### Android 12–16: all BLE advertisements remain at zero

On some Android BLE stacks a scan may start without callbacks when Location is switched off. Since this application does not use `neverForLocation` (which may filter scale advertisements), its scanner requests Nearby Devices (SCAN and CONNECT) and both coarse and precise Location. It does not request GPS coordinates or upload BLE data. Enable Location in Android quick settings and grant Precise Location to ROAST SCALE. The reception dashboard displays permission/location state and elapsed scan time. If no callback arrives after 12 seconds, it reports the stall instead of silently showing 'scanning'. This is a diagnosis aid, not a guarantee that a particular Xiaomi firmware will work.
