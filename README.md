# Virelix — AI-Assisted Android Video Editor

Virelix is a Kotlin + Jetpack Compose Android video editor built on Media3 Transformer and Firebase.

## Current features

- Import multiple videos
- Non-destructive trim and split
- Duplicate, delete, mute and speed controls
- 9:16 Shorts/Reels/TikTok presets
- 16:9 YouTube preset
- 4:5 feed preset
- Text overlay rendering
- Background music sequence during export
- AI edit-plan endpoint through Firebase Cloud Functions
- Anonymous Firebase authentication
- Firestore project metadata
- Firebase Storage export upload
- Share exported MP4 through FileProvider
- Undo/redo for timeline edits

## Before building

1. Create/configure a Firebase project.
2. Add Android app package `com.bimal.clipforge`.
3. Download `google-services.json`.
4. Put it at `ClipForge_V1/app/google-services.json`.
5. Enable Anonymous Authentication.
6. Enable Firestore and Storage.
7. Configure the `GEMINI_API_KEY` secret for Cloud Functions if AI editing is enabled.

The real Firebase configuration file is intentionally not committed to GitHub.

## Open in Android Studio

Open the `ClipForge_V1` directory as the Android Studio project and let Android Studio sync Gradle.

Use JDK 17 for the project toolchain.

## Run

Install on a physical Android device for best media testing. Test:

- video import
- trim/split
- speed
- text overlay
- background music
- multi-clip export
- share
- AI Edit

## Release

Update `versionCode` for every Play Store release. Generate a signed Android App Bundle (`.aab`) from Android Studio.

## Firebase Functions

From the repository root:

```bash
cd functions
npm install
npm run build
```

Deploy with Firebase CLI after authenticating:

```bash
firebase deploy --only functions
```

Do not put Gemini/OpenAI API keys in Android source code. Keep them in Firebase Functions secrets.
