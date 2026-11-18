# Poros builds and release signing

| Build | Application ID | Launcher name | Signing |
| --- | --- | --- | --- |
| Release | `com.jevon.personalos` | Poros | Permanent private release key |
| Debug | `com.jevon.personalos.dev` | Poros DEV | Existing Android debug key |

DEV has a visible DEV badge in both normal and themed launcher icons, plus DEV widget-picker
labels. Android isolates each package's Room database, DataStore, Glance state, widget instances,
PendingIntents, notifications and WorkManager jobs. The shared Kotlin namespace and `personal-os.db`
filename stay `com.jevon.personalos` and `personal-os.db` respectively.

Version `0.1.0` / code `1` remains the baseline in `app/build.gradle.kts`. Increase the checked-in
integer code for every distributed production release and use a semantic version name. Build the
same revision on both PCs. DEV adds `-dev` to that name.

## Create the permanent key privately

No Poros release key was supplied in Phase 5.4A. Run this once in your own local PowerShell terminal
with JDK `keytool` on PATH. It refuses to overwrite the chosen file. Enter passwords only at the
interactive prompts and keep them in a password manager. PKCS12 uses the store password for the key.

```powershell
$keyDirectory = Join-Path $env:USERPROFILE 'AndroidSigning'
$porosKey = Join-Path $keyDirectory 'poros-release.p12'
New-Item -ItemType Directory -Force -Path $keyDirectory | Out-Null
if (Test-Path -LiteralPath $porosKey) { throw 'Key already exists. Verify and reuse it.' }
keytool -genkeypair -keystore $porosKey -storetype PKCS12 -alias poros-release -keyalg RSA -keysize 3072 -validity 10000
if ($LASTEXITCODE -ne 0) { throw 'Key creation failed. Inspect the local result before retrying.' }
keytool -list -v -keystore $porosKey -alias poros-release
```

Record the alias `poros-release` and the displayed **SHA256** certificate fingerprint in a private
signing record. The fingerprint is available after creation. A public certificate fingerprint can
be shared for verification. Keep the keystore and passwords private. Android Studio's Generate
Signed Bundle / APK wizard can create the same permanent key with this alias and external location.

## Configure either PC

Create `keystore.properties` in the repository root using a local editor. Git ignores this file.
Use the actual private values locally and forward slashes in the path because Java properties
interpret backslashes as escapes.

```properties
storeFile=C:/Users/YOUR_USER/AndroidSigning/poros-release.p12
storePassword=YOUR_PRIVATE_STORE_PASSWORD
keyAlias=poros-release
keyPassword=YOUR_PRIVATE_STORE_PASSWORD
```

Alternatively set `POROS_KEYSTORE_FILE`, `POROS_STORE_PASSWORD`, `POROS_KEY_ALIAS` and
`POROS_KEY_PASSWORD` through your private local environment. Each environment value takes precedence
over its corresponding property. Avoid passing passwords as command-line arguments, saving them in
shell history, or publishing verbose Gradle logs/build scans. The keystore path is the only setting
that needs to differ between PCs. The key, alias and passwords must match.

From the repository root, with JDK/Android SDK configured, run

```powershell
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 :app:assembleDebug
rtk proxy powershell -NoProfile -ExecutionPolicy Bypass -File tools/gradle.ps1 :app:assembleRelease
```

Outputs are `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk`. Release builds explicitly use the release signing
configuration and disable debugging. Missing credentials or a missing keystore stop release
preparation with a clear error. Invalid passwords or aliases fail Android's signing step.

Copy the permanent keystore through an encrypted drive or trusted encrypted transfer. Configure
the other PC locally and keep an encrypted backup of the key separate from each PC. Retrieve
passwords from your password manager. Compare `keytool -list -v` SHA256 fingerprints on both PCs.
After building, also run the SDK build-tools `apksigner verify --print-certs` against each release
APK and compare its signer SHA-256 digest with that record. Never generate a replacement key merely
because another PC needs signing access. Losing the key or password prevents ordinary updates to
existing installations signed with it.

## Phase 5.4B data transition

The former debug package is `com.jevon.personalos.debug`. DEV's new `.dev` package starts with
independent storage and does not automatically acquire those records. A debug certificate and a
release certificate cannot update the same installed package through an ordinary APK replacement.
The old `.debug` package and the production package can coexist because their IDs differ. If an
existing installation already uses the production ID with a debug certificate, preserve a verified
portable export before any later uninstall decision.

In Phase 5.4B, export from the source app through Settings, copy the unencrypted JSON to trusted
storage, inspect it in the target app, and compare the preview counts before confirming full
replacement. Keep the source app and export until the target's records and balances are verified.
Restore replaces target records transactionally. It does not merge two installations. The existing
codec accepts formats 1–4, uses a fixed backup format identifier independent of application ID,
validates schema versions and SHA-256 integrity, and enforces 8 MiB / 20,000-record limits.

Exports include domain records, reminder completions and alert preferences. They exclude local
cover images, app settings/Today budget selection, Quick Expense presets and receipts, launcher
widget placements/configuration and runtime alert state. Recreate these settings and widgets in
the chosen target and review its notification permission. Keeping both apps' alerts enabled after
copying records can produce duplicate notifications. These are planning considerations for 5.4B.

Phase 5.4A performs configuration and local build checks. Device installation and data transition
are scheduled for Phase 5.4B.
