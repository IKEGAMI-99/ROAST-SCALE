# ROAST SCALE ⚖️

Android body-measurement journal for Xiaomi XMTZC02HM BLE smart scales, built with Kotlin and Jetpack Compose.

## Capabilities

- Bluetooth LE weight and impedance, with locally estimated BMI, body-fat percentage, water, muscle, bone and metabolism. No Xiaomi login.
- Manual waist circumference (cm), plus automatic waist-to-height ratio from the profile height.
- Seven-calendar-day rolling mean weight, using only one latest measurement per local day. Missing days are not fabricated.
- Dashboard with weight, compact two-column metrics, waist and rolling weight average.
- History charts for every composition metric plus separate waist, waist ratio and seven-day-average line graphs.
- Individual history and waist deletion with confirmation.
- ChatGPT sharing: explicitly copies a Japanese diet-advice prompt together with the entire CSV history to Android clipboard. It also launches a share chooser including the CSV file. Choose ChatGPT, or paste the copied content manually if your ChatGPT version does not import shared text and attachments.
- CSV-file-only export for spreadsheet tools and backups.
- GitHub Releases in-app update checking, APK SHA256 verification, and Android installer.

**No bundled AI model, Gemma or LiteRT-LM runtime.** Previous AI comments stored in the old database are preserved but no longer displayed or used.

## Privacy

Measurements and abdominal circumference remain local in app-private SQLite until you actively share them. When you choose ChatGPT or another sharing target, **your health data leaves the phone**, subject to the chosen service's privacy settings. Clipboard copy also exposes the data to the Android clipboard until replaced. The application itself has no health-data upload API.

Waist-to-height 0.5 is a common adult reference point, not a diagnosis. Estimated body-fat and muscle values are not clinically measured.

## Installation and migration

SQLite database version 2 adds a waist_measurements table without deleting or changing the original measurements. Normal same-signature app updates retain data and profile. Debug APKs can have incompatible Android signatures across builds; uninstalling to switch signatures deletes local measurements.

## Build

Use JDK 17, Gradle 8.14.3, Android SDK 36:

    gradle :app:testDebugUnitTest :app:assembleDebug

GitHub Actions builds/tests on every push. The signed-release job requires four private Actions secrets: SIGNING_KEYSTORE_BASE64, SIGNING_STORE_PASSWORD, SIGNING_KEY_PASSWORD and SIGNING_KEY_ALIAS. The private signing key must never be committed to this public repository. See:
https://github.com/IKEGAMI-99/ROAST-SCALE/settings/secrets/actions

A signed GitHub Release exists only when the release job succeeds; release checks inside the app require it.
