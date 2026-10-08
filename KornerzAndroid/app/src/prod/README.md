# Prod flavor — production Firebase

The `prod` product flavor (`com.kornerz.kornerz`) connects to the
**production** Firebase project.

`google-services.json` in this directory is committed to the repo. It
contains public OAuth client IDs and the Android API key, which are not
secret for mobile clients (they're restricted via Firebase Security
Rules + API key restrictions in the GCP / Firebase console).

If you need to rotate the production config, replace this file and
re-run `./gradlew :app:assembleProdDebug` to verify.