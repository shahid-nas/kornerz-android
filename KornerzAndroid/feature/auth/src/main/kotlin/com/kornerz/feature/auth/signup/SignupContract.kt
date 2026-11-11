package com.kornerz.feature.auth.signup

import android.net.Uri
import com.kornerz.core.model.AsyncValue

/**
 * Aggregate state for the 8-step signup wizard. Per the architecture
 * every step Composable reads its own subset of fields, validates
 * locally, and emits events through [SignupEvent].
 */
data class SignupWizardState(
    val step: Int = 1,
    val fullName: String = "",
    val nickname: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val phone: String = "",
    val countryDialCode: String = "+1",
    val countryIso: String = "US",
    val languages: Set<String> = emptySet(),
    /**
     * The names of the Kornerz the user picked on step 5.
     *
     * The values are real community names read from the `Communities`
     * collection (the approved ones), so they line up with the
     * `communityName` values the `Fellow` records are keyed by. They
     * are written to the user's `interests` field on signup.
     */
    val interests: Set<String> = emptySet(),
    /**
     * The picked profile photo.
     *
     * The photo is **not** uploaded here — the selection is only held
     * until the wizard is completed. The upload happens on the final
     * step, immediately before the account is written to the database
     * (mirrors iOS, where the upload lives inside `signup()`:
     * `kornerz/Kornerz/SwiftUI/OnBoarding/Signup/SignupViewModel.swift:174-208`).
     *
     * The value is a `file://` URI pointing at the app's own cache,
     * **not** the Photo Picker's content URI: the picker's read grant
     * is short-lived, and the wizard holds the selection until the
     * final step. The bytes are copied at selection time so the upload
     * always has a readable source.
     *
     * The URI is persisted via [SavedStateHandle] as a string and
     * restored with `Uri.parse`.
     */
    val photoUri: Uri? = null,
    /**
     * The public download URL of the uploaded profile photo.
     *
     * `null` until the upload succeeds. It is written to the user's
     * `firebaseImageUrl` field when the account is created.
     */
    val photoUrl: String? = null,
    /**
     * A human-readable description of a failed photo operation.
     *
     * Set when the picked image cannot be copied into the app's cache
     * (shown on the photo step) and when the upload fails during the
     * submit (surfaced through the `submit` error on the final step).
     */
    val photoError: String? = null,
    /**
     * The user's `codeUser` value.
     *
     * The code doubles as the Cloud Storage path prefix
     * (`users/{userCode}/profile/...`), so it must exist before the
     * upload starts and be carried into the signup request. It is
     * generated on the photo step when a photo is picked. iOS does the
     * same — `kornerz/Kornerz/SwiftUI/OnBoarding/Signup/SignupViewModel.swift:167,182`.
     */
    val userCode: String? = null,
    // `submit` defaults to `Success(Unit)` (a no-op success sentinel) so
    // that on screen entry the password step doesn't render the
    // "Creating account…" loading state. `submit()` flips it to
    // `Loading` only when the user actually taps "Create account".
    val submit: AsyncValue<Unit> = AsyncValue.Success(Unit),
    // Async existence checks for the wizard's nickname (step 6) and
    // email (step 2) steps. Defaults to `Success(Unit)` so the UI isn't
    // stuck in a checking state on screen entry. While the check is
    // running these are `Loading`; on a hit they become `Error`.
    val nicknameCheck: AsyncValue<Unit> = AsyncValue.Success(Unit),
    val emailCheck: AsyncValue<Unit> = AsyncValue.Success(Unit),
    val emailAlreadyInUse: Boolean = false,
    val nicknameError: String? = null,
    val interestsError: Boolean = false,
) {
    val isFullNameValid: Boolean get() {
        val trimmed = fullName.trim()
        if (trimmed.isEmpty()) return false
        // Mirrors iOS `SignupViewModel.isFullNameValid`: split on
        // whitespace (without filtering), require ≥2 components, and
        // match each component against the iOS regex. Empty components
        // (e.g. from consecutive spaces) are rejected by the regex
        // because it requires `^[a-zA-Z]+`.
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.size < 2) return false
        val tokenRegex = Regex("^[a-zA-Z]+(?:[\\s\\-\\']*[a-zA-Z]+)*$")
        return tokens.all { it.matches(tokenRegex) }
    }
    val isEmailValid: Boolean get() = email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"))
    val isPhoneValid: Boolean get() {
        // Mirrors iOS `SignupViewModel.isPhoneNoValid`: total digits
        // (country code + local number) must be 10–15, prefixed with
        // `+`. Strips spaces, hyphens, and parentheses before counting.
        val full = countryDialCode + phone.trim()
        val cleaned = full.replace(Regex("[\\s\\-()]"), "")
        return cleaned.matches(Regex("^\\+[0-9]{10,15}\$"))
    }
    val isLanguageValid: Boolean get() = languages.isNotEmpty()
    val isInterestsValid: Boolean get() = interests.isNotEmpty() && interests.size <= 5
    val isNicknameValid: Boolean get() = nickname.trim().length in 3..20 &&
        nickname.matches(Regex("^[a-zA-Z0-9_-]+$"))
    /**
     * The photo step is complete when an image has been picked.
     *
     * The photo is **not** uploaded on this step — the upload runs on
     * the final step, right before the account is registered, so the
     * step only gates on the selection itself. The step can also be
     * skipped entirely, in which case the account is created without an
     * avatar.
     */
    val isPhotoValid: Boolean get() = photoUri != null
    val isPasswordValid: Boolean get() = password.length >= 6 && password == confirmPassword

    /**
     * Wizard step → matching field validation. Mirrors the iOS flow
     * 1:1: Full Name → Email → Phone → Language → Kornerz → Nickname
     * → Photo → Password (terminal, submits the account).
     */
    val canContinue: Boolean
        get() = when (step) {
            1 -> isFullNameValid
            2 -> isEmailValid && !emailAlreadyInUse
            3 -> isPhoneValid
            4 -> isLanguageValid
            5 -> isInterestsValid
            6 -> isNicknameValid
            7 -> isPhotoValid
            8 -> isPasswordValid
            else -> false
        }
}

sealed interface SignupEvent {

    data class FullNameChanged(val value: String) : SignupEvent
    data class NicknameChanged(val value: String) : SignupEvent
    data class EmailChanged(val value: String) : SignupEvent
    data class PasswordChanged(val value: String) : SignupEvent
    data class ConfirmPasswordChanged(val value: String) : SignupEvent
    data class PhoneChanged(val value: String) : SignupEvent
    data class CountrySelected(val dialCode: String, val iso: String) : SignupEvent
    data class LanguageToggled(val code: String) : SignupEvent
    /**
     * Toggles a Kornerz selection on the signup Kornerz step.
     *
     * [name] is the community's `communityName` — the same value the
     * `Fellow` records use to reference a community.
     */
    data class InterestToggled(val name: String) : SignupEvent
    /**
     * A profile photo was picked on the photo step.
     *
     * The photo is only recorded here — it is uploaded on the final
     * step, right before the account is registered.
     */
    data class PhotoSelected(val uri: Uri) : SignupEvent
    data object Continue : SignupEvent
    data object SkipStep : SignupEvent
    data object Back : SignupEvent
    data object Submit : SignupEvent
    data object SuggestNickname : SignupEvent
}

sealed interface SignupNavEvent {

    data object NavigateToShell : SignupNavEvent

    data object NavigateBack : SignupNavEvent
}
