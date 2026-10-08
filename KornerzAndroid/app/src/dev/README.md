# Dev flavor — staging Firebase

The `dev` product flavor (`com.kornerz.kornerz.dev`) connects to the
**staging** Firebase project so developers never accidentally read or
write production data.

This folder must contain the staging project's `google-services.json`,
downloaded from the Firebase Console (Project settings → Your apps →
Android app → `google-services.json`).

Without it, builds fail with:

```
File google-services.json is missing. The Google Services Plugin cannot
function without it. Searched location: .../app/src/dev/google-services.json
```

## Setup

1. Ask the Android lead (or anyone with Editor access on the staging
   project) for the staging `google-services.json`.
2. Drop the file at `google-services.json` in this directory.
3. Run `./gradlew :app:assembleDevDebug` to verify.

## Verifying which project you hit at runtime

`KornerzApplication.onCreate()` logs the connected project:

```
I/KornerzApplication: FirebaseApp initialized for project=kornerz-staging
```

Compare that value to the expected project id when in doubt.