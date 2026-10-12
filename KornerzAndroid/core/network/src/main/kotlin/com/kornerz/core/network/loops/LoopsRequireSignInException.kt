package com.kornerz.core.network.loops

/**
 * Signals that a loops feed request cannot proceed because the user is
 * not signed in. iOS surfaces this as `error = .tryAgain` in
 * `LoopsTabViewModel.fetchUserLoops`; on Android we keep the
 * `Result.failure` path but throw this specific subclass so the UI can
 * render "Please sign in to view your loops" instead of the generic
 * "Loops failed to load" message.
 */
class LoopsRequireSignInException : IllegalStateException(
    "Sign in to view your loops",
)
