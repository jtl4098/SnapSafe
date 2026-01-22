# Releasing SnapSafe

This project publishes the SDK from the `:snapsafe-core` module. The sample app is `:snapsafe-samples`.

## Build the SDK AAR

```bash
./gradlew :snapsafe-core:assembleRelease
```

The AAR will be located at:

```
snapsafe-core/build/outputs/aar/snapsafe-core-release.aar
```

## JitPack
1) Push to GitHub and tag a release:

```bash
git tag v0.1.0
git push origin v0.1.0
```

2) JitPack will build with JDK 17 via `jitpack.yml`.

3) Add JitPack to repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

4) Add the dependency (replace with your GitHub user/org and tag):

```kotlin
dependencies {
    implementation("com.github.<GitHubUser>:SnapSafe:v0.1.0")
}
```

## Sanity checks
- `./gradlew :snapsafe-core:assembleRelease`
- `./gradlew :snapsafe-samples:assembleDebug`

## Notes
- The SDK is camera-agnostic; only the masked output should be uploaded.
- EXIF stripping is pending; document this in release notes.
