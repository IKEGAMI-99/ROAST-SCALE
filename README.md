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
- CSV import from the Android file picker (Settings): restore exported weights, estimated composition, timestamps and waist measurements, append-only with duplicate detection. Export-derived averages/ratios are recalculated; the profile is not included.
- GitHub Releases in-app update checking, APK SHA256 verification, and Android installer.

**No bundled AI model, Gemma or LiteRT-LM runtime.** Previous AI comments stored in the old database are preserved but no longer displayed or used.

## Privacy

Measurements and abdominal circumference remain local in app-private SQLite until you actively share them. When you choose ChatGPT or another sharing target, **your health data leaves the phone**, subject to the chosen service's privacy settings. Clipboard copy also exposes the data to the Android clipboard until replaced. The application itself has no health-data upload API.

Waist-to-height 0.5 is a common adult reference point, not a diagnosis. Estimated body-fat and muscle values are not clinically measured.

## Installation and migration

SQLite database version 2 adds a waist_measurements table without deleting or changing the original measurements. Normal same-signature app updates retain data and profile. Debug APKs can have incompatible Android signatures across builds; uninstalling to switch signatures deletes local measurements. **Before uninstalling a debug APK, export the full CSV from the dashboard and keep a safe copy.** After installing the first permanently signed release, open Settings → CSV backup restore and import that file. Re-enter your height/age/sex in Settings (profile metadata is not part of the CSV). Importing the same CSV twice does not duplicate identical timestamp/value pairs.

## Build

Use JDK 17, Gradle 8.14.3, Android SDK 36:

    gradle :app:testDebugUnitTest :app:assembleDebug

GitHub Actions builds/tests on every push. The signed-release job requires four private Actions secrets: SIGNING_KEYSTORE_BASE64, SIGNING_STORE_PASSWORD, SIGNING_KEY_PASSWORD and SIGNING_KEY_ALIAS. The private signing key must never be committed to this public repository. See:
https://github.com/IKEGAMI-99/ROAST-SCALE/settings/secrets/actions

### Configure a permanent signing key (one-time setup)

On your own computer with JDK installed, create a PKCS#12 key (choose a strong password and keep a private backup of the file and password):

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore roast-release.p12 \
  -alias roast -keyalg RSA -keysize 3072 -validity 10000
```

Prepare a one-line Base64 string of the keystore. On macOS use:

```bash
base64 < roast-release.p12 | tr -d '\n'
```

Create the following **repository Actions secrets** at https://github.com/IKEGAMI-99/ROAST-SCALE/settings/secrets/actions:

- `SIGNING_KEYSTORE_BASE64`: the full one-line Base64 output above
- `SIGNING_STORE_PASSWORD`: keystore password
- `SIGNING_KEY_PASSWORD`: private key password (normally the same as store password for PKCS#12)
- `SIGNING_KEY_ALIAS`: `roast`

Never commit, share publicly, or lose the key. Each update must use **the same keystore**, or Android will reject an in-place install. Once all secrets are saved, use the GitHub Actions `Android CI and GitHub Releases` workflow → `Run workflow` on `main`. It verifies tests and the signature, creates `roast-scale.apk` and `roast-scale.apk.sha256`, and publishes the next version to GitHub Releases. A signed release exists only when this job succeeds.

### CSV import rules

The import uses Android's document picker and reads files locally (10 MB / 50,000 data rows maximum). Files must have `kind`, `date_local`, `weight_kg` and `waist_cm_manual` columns as exported by ROAST SCALE. All rows are validated first. Any invalid row prevents the whole file from being imported. Inserts are transactional and duplicate checks compare the exported one-second timestamp with the three-decimal measurement value. Existing records are not deleted, and existing profiles are not replaced.
