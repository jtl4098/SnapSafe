# SnapSafe

SnapSafe is an Android open source SDK that masks faces and PII text on-device. It takes an image as input and returns a masked image, so only the redacted result is uploaded or stored remotely.

## Modules
- `:snapsafe-core` - SDK logic (masking, ML Kit integration)
- `:snapsafe-samples` - sample app using the SDK

## Install (local module)
If you are using this repo directly:

```kotlin
dependencies {
    implementation(project(":snapsafe-core"))
}
```

## Usage

```kotlin
val snapSafe = SnapSafe(context)

snapSafe.maskFile(file,
    onSuccess = { result ->
        val maskedBitmap = result.bitmap
        val faces = result.faceCount
        val texts = result.textCount
    },
    onError = { error ->
        // handle error
    }
)
```

Suspend API:

```kotlin
val snapSafe = SnapSafe(context)
val result = snapSafe.maskFile(file)
```

## Policy
- Text is masked with solid color (no blur).
- Faces are masked with pixelation (mosaic).

## Notes
- The SDK is camera-agnostic. It only accepts Bitmap/File/Uri inputs.
- Remember to upload only the masked output if you are sending photos to a server.

## License
Apache 2.0
