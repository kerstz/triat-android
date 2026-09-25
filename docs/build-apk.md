# Building an APK

The debug APK built by CI is enough to try the app. A release APK needs the
Triat signing key.

## Create the Triat key (once)

From the repository root:

```bash
mkdir -p keystore
keytool -genkeypair -v -keystore keystore/triat.jks -alias triat -keyalg RSA -keysize 4096 -validity 10000
```

**Back up `keystore/triat.jks` and its passwords outside the repository.
Losing them means no future APK can update the installed one.**

The `keystore/` folder is gitignored and must never be committed.

## Local builds

Add these three lines to `local.properties`:

```
keystore.storePassword=<password>
keystore.keyAlias=triat
keystore.keyPassword=<password>
```

Then run `./gradlew assembleRelease`. The APKs are written to
`app/build/outputs/apk/release/`, one per ABI plus a universal one. On a
recent phone, use the one whose name contains `arm64-v8a`.

## CI builds

Set four secrets on the GitHub repository: `KEYSTORE_BASE64` (the output of
`base64 -w0 keystore/triat.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD`. Without them, CI still produces a release APK, signed with the
debug key.

## Without the Triat key

When `keystore/triat.jks` or one of its credentials is missing, the release
build is signed with the debug key instead of failing. Such an APK installs,
but Android refuses to update it with an APK signed by the real key (and the
other way round): uninstall the app first.
