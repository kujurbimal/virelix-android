# Virelix: GitHub → Firebase → Android Studio → Google Play

## 1. GitHub

Recommended repository:

`virelix-android`

Commit source code, but never commit:

- `google-services.json`
- keystore files
- API keys
- local.properties

## 2. Firebase

Create or use a Firebase project and register:

`com.bimal.clipforge`

Enable:

- Anonymous Authentication
- Firestore
- Storage
- Cloud Functions

Set `GEMINI_API_KEY` as a Functions secret if AI features are enabled.

## 3. Android Studio

Open `ClipForge_V1`.

Add:

`app/google-services.json`

Sync Gradle and run on a real Android device.

## 4. Test the editor

Test import, multi-clip timeline, trim, split, speed, text, music, AI Edit, export and share.

## 5. Play Store

Create a signed `.aab`, upload to internal/closed testing, complete the Play Console declarations, privacy policy and Data safety form, then progress through testing and production release requirements.

## Important

The current app package remains `com.bimal.clipforge` so an existing Firebase Android registration can continue to be used. The public app name is now **Virelix**. If you want a brand-new package ID later, register a new Firebase Android app and migrate the package consistently.
