Project Blueprint: SnapSafe SDK (Android)
"Shoot first, ask questions later? No. Shoot first, secure instantly."

1. Executive Summary
SnapSafe SDK is an Android Open Source Project (AOSP) designed to resolve the modern conflict between visual documentation and data privacy. It provides a "drop-in" camera solution that automatically identifies and redacts sensitive information, specifically human faces and text containing PII (Personally Identifiable Information), within milliseconds of image capture.

Current solutions are fragmented: they are either post-processing apps (slow, manual) or expensive commercial SDKs. This project fills the "Open Source Gap" by providing a high-performance, on-device library that handles both Portrait Rights (Vivaloca scenario) and Financial Privacy (Danggeun scenario).

2. Background & Problem Statement
2.1. The Legal Landscape
South Korea (Portrait Rights): Under the Constitution and strict judicial precedents, photographing identifiable individuals in public spaces (e.g., restaurants) without consent can lead to civil liability and "deletion requests".

GDPR (Europe): Faces are considered biometric data. Processing them without "legitimate interest" or explicit consent violates GDPR Article 6. Incidental capture in commercial environments often lacks this legitimate basis.

Financial Risk: High-resolution cameras can capture credit card numbers and QR codes from meters away, leading to fraud and theft in C2C marketplaces.

2.2. The UX Latency Trap
Existing open-source implementations typically follow a Capture -> Load -> Process -> Save workflow, resulting in a 1-2 second "Shutter Lag." This destroys the user experience (UX) and leads to user abandonment.

Our Goal: Achieve <100ms perceived latency by leveraging pre-processing techniques, ensuring the "Safe Photo" feels as instant as a raw photo.

3. User Scenarios & Requirements
Metric	Scenario A: "Vivaloca" (Restaurant)	Scenario B: "Danggeun" (Marketplace)
Target	Bystander Faces (Portrait Rights)	Credit Cards, Receipts, shipping labels
Output Style	Aesthetic Blur / Bokeh / Mosaic	Solid Masking (Black Box)
Security Level	Medium (Reversibility is a concern)	Critical (Zero leakage allowed)
Key Challenge	Low light, crowded scenes, false negatives	Glare on embossed cards, angled text
Detection Engine	ML Kit Face Detection (Unbundled)	ML Kit Text Recognition V2 (Latin/Korean)

4. Implementation Roadmap
We will adopt a "Crawl, Walk, Run" strategy to manage complexity.

Phase 1: MVP - "Post-Processing Reliability" (The Crawl)
Objective: Validate the accuracy of ML Kit and the masking logic. Ignore speed for now.

Workflow:
- Standard ImageCapture via CameraX.
- Load the full Bitmap into memory (carefully downsampled).
- Run ML Kit detection (Async).
- Draw masks on a Canvas.
- Display result.

Success Metric: 99% detection rate on credit cards; no OOM (Out Of Memory) crashes on 12MP images.

Phase 2: Interactive - "Real-time Confidence" (The Walk)
Objective: Show the user what will be hidden before they shoot.

Workflow:
- Bind ImageAnalysis use case to CameraX.
- Process low-res preview frames (e.g., 640x480).
- Draw a Bounding Box Overlay on the UI (PreviewView).

Technical Hurdle: Coordinate Mapping. Transforming coordinates from the Analysis buffer (640x480) to the View system (1080x1920) is notoriously difficult due to aspect ratio cropping (CenterCrop vs FitCenter).

Phase 3: High Performance - "Zero Shutter Lag" (The Run)
Objective: The "0.1s" magic.

Workflow:
- Maintain a "Latest Valid Mask" cache from Phase 2 (Preview stream).
- When the user taps the shutter, DO NOT run detection again.
- Take the cached coordinates from the Preview stream, scale them to the 4K captured image, and apply the mask immediately.

Benefit: Reduces processing time from ~1000ms (OCR) to ~10ms (Bitmap Drawing).

Current MVP Status (app module)
- CameraX preview + ImageCapture working.
- Capture -> ML Kit Face + Text -> Mask -> Result screen wired.
- Masking rules: text = solid fill, face = pixelation.
- Debug build draws colored outlines for masks.
- "Load" button lets emulator test from gallery/files without a camera.

5. Technical Architecture & Stack
Language: Kotlin 100%

Min SDK: API 34 (current app config; lower later if needed)
Compile SDK: 36

Camera: Android CameraX (Lifecycle-aware, vendor extensions support).

AI Engine: Google ML Kit
- Face: Unbundled version (Downloads dynamically via Play Services).
- Text: play-services-mlkit-text-recognition (V2).

Image Processing:
- Faces: Pixelation (Mosaic) for now.
- Text: Solid color mask (no blur).
- Exif: rotation handled for decoding; stripping pending.

Build Tooling (current):
- Android Gradle Plugin 8.9.1
- Gradle 8.11.1

6. Critical Technical "Pain Points" (The Watchlist)
6.1. The "Embossed Card" Problem
Credit cards with raised (embossed) numbers often fail OCR because shadows make "8" look like "B" or "3".

Solution: Implement Regex Fuzzy Matching (Luhn Algorithm for card validation) and aggressive masking. If a block looks 50% like a card pattern, mask the whole block.

6.2. Blurring is NOT Secure
Research shows that Gaussian Blur can be reversed by Generative AI (GANs).

Policy:
- Text: NEVER blur. Use Solid Color (Black/White).
- Faces: Apply Pixelation (Mosaic) combined with Random Noise Injection before blurring to break AI reconstruction patterns.

6.3. Memory Management (OOM)
Loading a 12MP image (4000x3000) requires ~48MB of RAM. Making a mutable copy for blurring doubles this to ~96MB.

Solution: Use BitmapFactory.Options.inSampleSize to process a proxy image for detection, or use BitmapRegionDecoder to modify only specific chunks of the image.

7. Open Source Strategy
License: Apache 2.0. (Permissive license to encourage adoption by commercial startups like Vivaloca/Danggeun).

Module Structure:
- Current: Single :app module with core/ui packages.
- Future:
  - :snapsafe-core (The logic, no UI)
  - :snapsafe-ui (CameraX Viewfinder + Overlay Views)
  - :snapsafe-demo (Example implementations of the two scenarios)

8. Development Checklist
[x] Setup: Initialize CameraX with Preview + ImageCapture use cases.
[x] AI: Integrate ML Kit Face (Unbundled) and Text Recognition V2.
[ ] Math: Implement CoordinateTransform class to map Preview -> Capture coordinates.
[x] Graphics: Implement a Masker class that accepts a Bitmap + List and returns a new Bitmap.
[ ] Security: Implement Exif stripper to remove location metadata from protected images.
[x] MVP: Capture -> Detect -> Mask -> Result flow in app.
[x] MVP: Load test image from storage (emulator-friendly).
[ ] Phase 2: ImageAnalysis + overlay bounding boxes.

Developer Note: Start with Phase 1. Do not optimize for speed until the detection logic is robust. The value of this library lies in its reliability (not leaking data) first, and its speed second.

