package com.kornerz.core.network.loops

/**
 * Signals that a loops feed request cannot proceed because the signed-in
 * user has no usable identity for the `publisherId` match.
 *
 * iOS surfaces this as `error = .tryAgain` in
 * `LoopsTabViewModel.fetchUserLoops`; on Android we keep the
 * `Result.failure` path but throw this specific subclass so the UI can
 * render "Please sign in to view your loops" instead of the generic
 * "Loops failed to load" message.
 *
 * The trigger is the absence of a user **`codeUser`** — the value loop
 * documents store in `publisherId` — not the absence of a Firebase Auth
 * session. The app signs in anonymously, so an Auth uid is always
 * present and never identifies the loop author.
 */
class LoopsRequireSignInException : IllegalStateException(
    "Sign in to view your loops",
)
