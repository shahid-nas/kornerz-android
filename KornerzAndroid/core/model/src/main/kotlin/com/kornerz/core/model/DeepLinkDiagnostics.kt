package com.kornerz.core.model

/**
 * The deep-link chain's diagnostics sink.
 *
 * `core/model` is a **pure JVM module** — it has no Android dependency
 * and therefore cannot write to logcat. The parser lives here, but the
 * Android side owns the logging, so the parser reports what it did
 * through this sink and the Android side decides how to print it.
 *
 * The sink is deliberately tiny: a single nullable callback. An app that
 * does not care about deep-link diagnostics simply never sets it, and
 * the parser's reporting costs one null check.
 *
 * The Android implementation lives in `core/ui` as `DeepLinkLog`, which
 * writes to logcat under the single `DeepLink` tag.
 */
object DeepLinkDiagnostics {

    /**
     * Reports what a stage of the chain did.
     *
     * @param stage the numbered stage, e.g. `"5-parse"`.
     * @param message what happened at this stage.
     */
    fun interface Reporter {
        fun report(stage: String, message: String)
    }

    /** The active reporter, or `null` when nothing is listening. */
    @Volatile
    var reporter: Reporter? = null

    /** Reports a stage that completed. */
    fun log(stage: String, message: String) {
        reporter?.report(stage, message)
    }

    /** Reports a stage that failed. */
    fun fail(stage: String, message: String) {
        reporter?.report(stage, "FAILED: $message")
    }

    /** Reports a stage that was skipped. */
    fun skip(stage: String, message: String) {
        reporter?.report(stage, "SKIPPED: $message")
    }
}
