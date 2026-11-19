# Poros builds and release signing

| Build | Application ID | Launcher name | Signing |
| --- | --- | --- | --- |
| Release | `com.jevon.personalos` | Poros | Permanent private release key |
| Debug | `com.jevon.personalos.dev` | Poros DEV | Existing Android debug key |

DEV has a badge in normal and themed launcher icons and on the Android 12+ splash screen. Its four
widgets have DEV picker labels and warm-toned backgrounds in light and dark mode. Quick Expense also
shows the variant name in its header when the widget has room for one.

Android isolates each package's Room database, DataStore, local cover files, Glance state, widget
instances, PendingIntents, notifications and WorkManager jobs. Widgets open explicit components in
their own package. Android pairs Quick Expense's configuration class with the widget provider's
installed package. The Kotlin namespace stays `com.jevon.personalos` and each package uses its own
private `personal-os.db` database. The debug review provider is private and uses a variant-specific
authority. Manual exports use Android's document picker and suggest `poros-*.json` or
`poros-dev-*.json`. Either build can inspect a backup the user explicitly selects.

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

Earlier builds used `com.jevon.personalos.debug`. Also check for `com.jevon.personalos` and
`com.jevon.personalos.dev` on Samsung with `adb shell pm list packages com.jevon.personalos`.
Confirm which installation holds the personal records. Use `adb shell pm path <package>` to find
its base APK, then pull that APK and use `apksigner verify --print-certs` to record its signer
SHA-256 before planning an update. Independent packages can coexist. Ordinary in-place replacement
requires the installed package's signing identity. A production-ID installation with a different
certificate requires a separately approved transition that clears its private storage.

1. In the source app, use **Settings → Backup & restore → Export backup**. Keep a copy of the
   unencrypted JSON in trusted storage outside the source package. Record the source totals and
   counts you expect to see after restore. Record any local configuration and identify which
   original cover images are available to reattach.
2. Before any removal, inspect the export in the source app or a separately installed target. Check
   the timestamp and record counts against the source and exit the preview. If removal is required,
   obtain approval for the uncovered data and rehearse a restore in a separate installation before
   touching the source. Stop if the export, rehearsal, or recovery plan fails.
3. Inspect the verified export in the approved target, then confirm replacement. Compare record
   counts, balances, and representative archived and scheduled items. Reattach available covers,
   recreate settings and widgets, and check notification permissions. Keep the verified backup.
   Where the IDs differ, keep the source installed until the target is verified for an easy fallback.

Restore replaces target records transactionally. The existing codec accepts formats 1–4, uses a
fixed backup format identifier across variants, validates schema versions and SHA-256 integrity,
and enforces 8 MiB / 20,000-record limits.

Exports include domain records, reminder completions and alert rules. They exclude local Library
cover files and paths, app settings including appearance and Today budget selection, Quick Expense
presets and receipts, launcher widget placement/configuration, notification permissions, and runtime
alert state. Android automatic backup and device transfer are disabled by the app's backup
configuration. Keeping both apps' alerts enabled after copying records can produce duplicate
notifications.

Phase 5.4A permits installation of the DEV APK alongside the source when Samsung is connected.
The production install and any data transition require separate approval in Phase 5.4B.
