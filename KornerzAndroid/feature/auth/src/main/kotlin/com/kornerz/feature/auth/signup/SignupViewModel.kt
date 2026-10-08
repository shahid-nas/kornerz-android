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
 * Single ViewModel for the 9-step signup wizard. Holds a
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
            is SignupEvent.NicknameChanged -> _state.update { it.copy(nickname = event.value) }
            is SignupEvent.EmailChanged -> _state.update {
                it.copy(email = event.value, emailAlreadyInUse = false)
            }
            is SignupEvent.PasswordChanged -> _state.update { it.copy(password = event.value) }
            is SignupEvent.ConfirmPasswordChanged -> _state.update { it.copy(confirmPassword = event.value) }
            is SignupEvent.PhoneChanged -> _state.update { it.copy(phone = event.value) }
            is SignupEvent.CountrySelected -> _state.update {
                it.copy(countryDialCode = event.dialCode, countryIso = event.iso)
            }
            is SignupEvent.LanguageChanged -> _state.update { it.copy(language = event.value) }
            is SignupEvent.InterestToggled -> _state.update {
                val next = it.interests.toMutableSet()
                if (next.contains(event.slug)) {
                    next.remove(event.slug)
                } else if (next.size < 5) {
                    next.add(event.slug)
                }
                it.copy(interests = next)
            }
            is SignupEvent.PhotoSelected -> _state.update {
                it.copy(photoUri = event.uri, photoUpload = AsyncValue.Loading)
            }
            SignupEvent.PhotoUploadRetry -> uploadPhoto()
            SignupEvent.Continue -> advance()
            SignupEvent.Back -> back()
            SignupEvent.Submit -> submit()
        }
    }

    private fun advance() {
        val current = _state.value
        if (!current.canContinue) return
        persist(current.copy(step = current.step + 1))
    }

    private fun back() {
        val current = _state.value
        if (current.step == 1) {
            _navEvents.trySend(SignupNavEvent.NavigateBack)
            return
        }
        persist(current.copy(step = current.step - 1))
    }

    private fun submit() {
        val current = _state.value
        if (!current.isStep9Valid) return
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
                    language = current.language,
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
        savedStateHandle[KEY_LANGUAGE] = state.language
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
            language = savedStateHandle.get<String>(KEY_LANGUAGE) ?: "en",
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
        const val KEY_LANGUAGE = "signup.language"
        const val KEY_INTERESTS = "signup.interests"
    }
}