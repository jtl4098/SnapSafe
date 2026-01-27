# Releasing ImageRedact

This project publishes the SDK from the `:ImageRedact-core` module. The sample app is `:ImageRedact-samples`.

## Build the SDK AAR

```bash
./gradlew :ImageRedact-core:assembleRelease
```

The AAR will be located at:

```
ImageRedact-core/build/outputs/aar/ImageRedact-core-release.aar
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
    implementation("com.github.<GitHubUser>:ImageRedact:v0.1.0")
}
```

## Sanity checks
- `./gradlew :ImageRedact-core:assembleRelease`
- `./gradlew :ImageRedact-samples:assembleDebug`

## Notes
- The SDK is camera-agnostic; only the masked output should be uploaded.
- EXIF stripping is pending; document this in release notes.

