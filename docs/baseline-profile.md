# Baseline profile (`baselineprofile/`)

Markers mean what `CLAUDE.md` says they mean.

**State [October 2026]: no profile is committed.** The generator (`baselineprofile/src/main/java/.../BaselineProfileGenerator.kt`) exists and the app depends on it (`baselineProfile(project(":baselineprofile"))`), but nothing under `app/src/release/generated/baselineProfiles/` has ever been generated and committed, so release builds ship without an ART profile and the cold start interprets then JITs every Compose class it touches.

**Generation cannot happen on a normal machine session of this repo.** It needs a rooted or userdebug device and a minified release build, and local work stops before packaging and R8 (`CLAUDE.md` section 1). Two ways remain:

1. **CI, manual** [judgement October 2026]: run the `Generate baseline profile` workflow (`.github/workflows/baseline-profile.yml`, `workflow_dispatch`). It boots the Gradle managed device `pixel6Api34` (API 34, AOSP image, rootable) with KVM and SwiftShader, runs `:app:generateReleaseBaselineProfile -Ptriat.baselineProfile.managed`, and uploads the result as the `baseline-profile` artifact. Not yet run: whether the managed emulator boots within the setup timeout on a hosted runner is unverified.
2. **Maintainer's device**: a userdebug or `google_apis` AVD (the `Triat_Bench` AVD, android-36), never a Google Play image (`Pixel_10` refuses `adb root`). `./gradlew :app:generateReleaseBaselineProfile` with the device connected.

Then copy `baseline-prof.txt` and `startup-prof.txt` into `app/src/release/generated/baselineProfiles/`, read them (the rules should name `com.ivor.ivormusic` classes from Home, Search, Library and Settings), commit, and rebuild in CI. The profile is architecture-independent: one recorded on x86_64 is valid on arm64.

**The journey is deliberately signed-out.** The generator walks Home, Search, Library and Settings by their French or English tab label and skips a tab it cannot find, because a missing screen costs some coverage while a thrown exception costs the whole profile. Anything needing a YouTube session would record a path a fresh device cannot reach.

**Measuring the gain** needs a Macrobenchmark startup test on a physical device; none exists yet. Any startup figure quoted without one is an estimate.
