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

## Signing / release setup

The Android signing key is fixed across versions but NEVER placed in the APK or git repository.

Generate your own keystore once, and back it up offline:

    keytool -genkeypair -v -keystore roast-release.jks -alias roast -keyalg RSA -keysize 3072 -validity 10000

In GitHub repository Settings → Secrets and variables → Actions, configure four secrets:

- SIGNING_KEYSTORE_BASE64 (base64 encoded JKS contents)
- SIGNING_STORE_PASSWORD
- SIGNING_KEY_PASSWORD
- SIGNING_KEY_ALIAS

To encode the keystore on Linux:

    base64 -w 0 roast-release.jks

On macOS:

    base64 < roast-release.jks | tr -d '\n'

Push tag v0.1.0. On successful test and release jobs, the workflow publishes roast-scale.apk and roast-scale.apk.sha256 in Releases. Later versions should use monotonically increasing vMAJOR.MINOR.PATCH, with minor/patch less than 100, to generate increasing Android versionCodes. Always reuse the same keystore. The initial tag should correspond to the code in main.

In-app checks use the public GitHub Releases REST API. A private repository requires a different authenticated update mechanism. Installing any APK requires user confirmation and Android enforces matching APK signing certificates.

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
