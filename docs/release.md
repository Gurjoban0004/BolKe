# Release build

Release lint is enforced and debug signing is never used for release builds.

Provide the private service base URL and release keystore values outside source control:

```text
./gradlew clean lintRelease assembleRelease \
  -PlanguageServiceUrl=https://language.example \
  -PbolkeReleaseStoreFile=/absolute/path/to/bolke-release.jks \
  -PbolkeReleaseStorePassword=... \
  -PbolkeReleaseKeyAlias=... \
  -PbolkeReleaseKeyPassword=...
```

Without all four signing properties, Gradle intentionally produces an unsigned release APK for local verification. Never put passwords or the keystore in this repository.

Before distribution, verify the APK signature and test a clean install, upgrade, keyboard enablement, microphone denial/revocation, copied-message opt-in, offline behavior, private-service outage, and secure password/PIN fields.
