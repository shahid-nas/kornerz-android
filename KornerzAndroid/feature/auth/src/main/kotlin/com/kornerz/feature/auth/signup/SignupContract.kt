package com.kornerz.feature.auth.signup

import android.net.Uri
import com.kornerz.core.model.AsyncValue

/**
 * Aggregate state for the 9-step signup wizard. Per the architecture
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
    val language: String = "en",
    val interests: Set<String> = emptySet(),
    val photoUri: Uri? = null,
    val photoUpload: AsyncValue<String> = AsyncValue.Loading,
    val policyScrolledToEnd: Boolean = false,
    val submit: AsyncValue<Unit> = AsyncValue.Loading,
    val emailAlreadyInUse: Boolean = false,
) {
    val isStep1Valid: Boolean get() = fullName.trim().split(" ").filter { it.isNotBlank() }.size >= 2
    val isStep2Valid: Boolean get() = nickname.trim().length in 3..20 &&
        nickname.matches(Regex("^[a-zA-Z0-9_-]+$"))
    val isStep3Valid: Boolean get() = email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"))
    val isStep4Valid: Boolean get() = password.length >= 6 && password == confirmPassword
    val isStep5Valid: Boolean get() = phone.trim().length in 6..15
    val isStep7Valid: Boolean get() = language.isNotBlank()
    val isStep8Valid: Boolean get() = interests.isNotEmpty() && interests.size <= 5
    val isStep9Valid: Boolean get() = policyScrolledToEnd && submit !is AsyncValue.Loading

    val canContinue: Boolean
        get() = when (step) {
            1 -> isStep1Valid
            2 -> isStep2Valid
            3 -> isStep3Valid && !emailAlreadyInUse
            4 -> isStep4Valid
            5 -> isStep5Valid
            6 -> true
            7 -> isStep7Valid
            8 -> isStep8Valid
            9 -> isStep9Valid
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
    data class LanguageChanged(val value: String) : SignupEvent
    data class InterestToggled(val slug: String) : SignupEvent
    data class PhotoSelected(val uri: Uri) : SignupEvent
    data object PhotoUploadRetry : SignupEvent
    data class PolicyScrolledToEnd(val value: Boolean) : SignupEvent
    data object Continue : SignupEvent
    data object Back : SignupEvent
    data object Submit : SignupEvent
}

sealed interface SignupNavEvent {

    data object NavigateToShell : SignupNavEvent

    data object NavigateBack : SignupNavEvent
}