package com.kornerz.feature.auth.signup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.AuthRepository
import com.kornerz.core.network.auth.dto.SignupRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single ViewModel for the 8-step signup wizard. Holds a
 * [SignupWizardState] across configuration changes and process death
 * using [SavedStateHandle] (architecture §8.3). Emits navigation through
 * [SignupNavEvent] on `Channel<NavEvent>`.
 */
@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(restoreOrInit())
    val state: StateFlow<SignupWizardState> = _state.asStateFlow()

    private val _navEvents = Channel<SignupNavEvent>(Channel.BUFFERED)
    val navEvents: Flow<SignupNavEvent> = _navEvents.receiveAsFlow()

    fun onEvent(event: SignupEvent) {
        when (event) {
            is SignupEvent.FullNameChanged -> _state.update { it.copy(fullName = event.value) }
            is SignupEvent.NicknameChanged -> _state.update {
                it.copy(nickname = event.value, nicknameError = null)
            }
            is SignupEvent.EmailChanged -> _state.update {
                it.copy(email = event.value, emailAlreadyInUse = false)
            }
            is SignupEvent.PasswordChanged -> _state.update { it.copy(password = event.value) }
            is SignupEvent.ConfirmPasswordChanged -> _state.update { it.copy(confirmPassword = event.value) }
            is SignupEvent.PhoneChanged -> _state.update { it.copy(phone = event.value) }
            is SignupEvent.CountrySelected -> _state.update {
                it.copy(countryDialCode = event.dialCode, countryIso = event.iso)
            }
            is SignupEvent.LanguageToggled -> _state.update {
                val next = it.languages.toMutableSet()
                if (next.contains(event.code)) next.remove(event.code) else next.add(event.code)
                it.copy(languages = next)
            }
            is SignupEvent.InterestToggled -> _state.update {
                val next = it.interests.toMutableSet()
                if (next.contains(event.slug)) {
                    next.remove(event.slug)
                    it.copy(interests = next, interestsError = false)
                } else if (next.size < 5) {
                    next.add(event.slug)
                    it.copy(interests = next, interestsError = false)
                } else {
                    it.copy(interestsError = true)
                }
            }
            is SignupEvent.PhotoSelected -> {
                _state.update { it.copy(photoUri = event.uri) }
                uploadPhoto()
            }
            SignupEvent.PhotoUploadRetry -> uploadPhoto()
            SignupEvent.Continue -> advance()
            SignupEvent.SkipStep -> skipStep()
            SignupEvent.Back -> back()
            SignupEvent.Submit -> submit()
            SignupEvent.SuggestNickname -> suggestNickname()
        }
    }

    /**
     * Generates a nickname suggestion from the current full name and
     * writes it to `state.nickname`. Mirrors iOS
     * `SignupViewModel.suggestNickName()`: if the full name has exactly
     * two whitespace-separated tokens, the suggestion is generated from
     * the first token; otherwise from the entire full-name string.
     */
    fun suggestNickname() {
        val current = _state.value
        val suggestion = NicknameGenerator.suggestFromFullName(current.fullName)
        if (suggestion.isNotEmpty()) {
            _state.update { it.copy(nickname = suggestion, nicknameError = null) }
        }
    }

    private fun advance() {
        val current = _state.value
        // The password step (step 8) is the terminal step — its
        // "Create account" button submits the wizard rather than
        // advancing to a non-existent step 9 screen. Mirrors iOS where
        // the password step is the last in the wizard and the create-
        // account action lives there.
        if (current.step == 8) {
            submit()
            return
        }
        if (!current.canContinue) return
        val next = current.copy(step = current.step + 1)
        _state.update { next }
        persist(next)
    }

    /**
     * Advance to the next step bypassing the per-step validation gate.
     * Only the phone (step 3) and photo (step 7) steps expose a Skip
     * button in iOS, so the handler is a no-op everywhere else.
     */
    private fun skipStep() {
        val current = _state.value
        if (current.step != 3 && current.step != 7) return
        val next = current.copy(step = current.step + 1)
        _state.update { next }
        persist(next)
    }

    private fun back() {
        val current = _state.value
        if (current.step == 1) {
            _navEvents.trySend(SignupNavEvent.NavigateBack)
            return
        }
        val next = current.copy(step = current.step - 1)
        _state.update { next }
        persist(next)
    }

    private fun submit() {
        val current = _state.value
        if (!current.isPasswordValid) return
        _state.update { it.copy(submit = AsyncValue.Loading) }
        viewModelScope.launch {
            val result = authRepository.signup(
                SignupRequest(
                    displayName = current.fullName,
                    nickname = current.nickname,
                    email = current.email,
                    password = current.password,
                    phone = current.countryDialCode + current.phone,
                    countryCode = current.countryIso,
                    // iOS sends a list (`countries: languages`); the
                    // current backend DTO accepts a single `language`
                    // string, so we send the first selected code (or
                    // "en" as a default fallback). The data model can
                    // be widened to `List<String>` in a follow-up.
                    language = current.languages.firstOrNull() ?: "en",
                    interests = current.interests.toList(),
                    photoUrl = null,
                ),
            )
            _state.update {
                it.copy(
                    submit = result,
                    emailAlreadyInUse = (result as? AsyncValue.Error)
                        ?.throwable?.message?.contains("already registered") == true,
                )
            }
            if (result is AsyncValue.Success) {
                _navEvents.trySend(SignupNavEvent.NavigateToShell)
            }
        }
    }

    private fun uploadPhoto() {
        // No-op placeholder for P1: the photo upload Cloud Function path
        // lands in P2 (signed URL flow). For P1 we accept the local URI as
        // a future-upload marker.
        _state.update { it.copy(photoUpload = AsyncValue.Success("local-uri")) }
    }

    private fun persist(state: SignupWizardState) {
        savedStateHandle[KEY_STEP] = state.step
        savedStateHandle[KEY_FULL_NAME] = state.fullName
        savedStateHandle[KEY_NICKNAME] = state.nickname
        savedStateHandle[KEY_EMAIL] = state.email
        savedStateHandle[KEY_PASSWORD] = state.password
        savedStateHandle[KEY_CONFIRM_PASSWORD] = state.confirmPassword
        savedStateHandle[KEY_PHONE] = state.phone
        savedStateHandle[KEY_COUNTRY_DIAL] = state.countryDialCode
        savedStateHandle[KEY_COUNTRY_ISO] = state.countryIso
        savedStateHandle[KEY_LANGUAGES] = state.languages.toTypedArray()
        savedStateHandle[KEY_INTERESTS] = state.interests.toTypedArray()
    }

    private fun restoreOrInit(): SignupWizardState {
        val step = savedStateHandle.get<Int>(KEY_STEP) ?: 1
        return SignupWizardState(
            step = step,
            fullName = savedStateHandle.get<String>(KEY_FULL_NAME).orEmpty(),
            nickname = savedStateHandle.get<String>(KEY_NICKNAME).orEmpty(),
            email = savedStateHandle.get<String>(KEY_EMAIL).orEmpty(),
            password = savedStateHandle.get<String>(KEY_PASSWORD).orEmpty(),
            confirmPassword = savedStateHandle.get<String>(KEY_CONFIRM_PASSWORD).orEmpty(),
            phone = savedStateHandle.get<String>(KEY_PHONE).orEmpty(),
            countryDialCode = savedStateHandle.get<String>(KEY_COUNTRY_DIAL) ?: "+1",
            countryIso = savedStateHandle.get<String>(KEY_COUNTRY_ISO) ?: "US",
            languages = (savedStateHandle.get<Array<String>>(KEY_LANGUAGES)?.toSet() ?: emptySet()),
            interests = (savedStateHandle.get<Array<String>>(KEY_INTERESTS)?.toSet() ?: emptySet()),
        )
    }

    private companion object {
        const val KEY_STEP = "signup.step"
        const val KEY_FULL_NAME = "signup.fullName"
        const val KEY_NICKNAME = "signup.nickname"
        const val KEY_EMAIL = "signup.email"
        const val KEY_PASSWORD = "signup.password"
        const val KEY_CONFIRM_PASSWORD = "signup.confirmPassword"
        const val KEY_PHONE = "signup.phone"
        const val KEY_COUNTRY_DIAL = "signup.countryDialCode"
        const val KEY_COUNTRY_ISO = "signup.countryIso"
        const val KEY_LANGUAGES = "signup.languages"
        const val KEY_INTERESTS = "signup.interests"
    }
}
