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
    val interests: Set<String> = emptySet(),
    val photoUri: Uri? = null,
    val photoUpload: AsyncValue<String> = AsyncValue.Loading,
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
    val isPhotoValid: Boolean get() = photoUri != null &&
        photoUpload is AsyncValue.Success
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
    data class InterestToggled(val slug: String) : SignupEvent
    data class PhotoSelected(val uri: Uri) : SignupEvent
    data object PhotoUploadRetry : SignupEvent
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
