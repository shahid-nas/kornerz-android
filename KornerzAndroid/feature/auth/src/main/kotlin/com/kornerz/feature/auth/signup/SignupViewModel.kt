package com.kornerz.feature.auth.signup

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.network.AuthException
import com.kornerz.core.network.AuthRepository
import com.kornerz.core.network.auth.dto.SignupRequest
import com.kornerz.core.network.storage.SignupPhotoStorage
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
    private val photoStorage: SignupPhotoStorage,
    private val imageDecoder: SignupImageDecoder,
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
                it.copy(
                    nickname = event.value,
                    nicknameError = null,
                    nicknameCheck = AsyncValue.Success(Unit),
                )
            }
            is SignupEvent.EmailChanged -> _state.update {
                it.copy(
                    email = event.value,
                    emailAlreadyInUse = false,
                    emailCheck = AsyncValue.Success(Unit),
                )
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
                if (next.contains(event.name)) {
                    next.remove(event.name)
                    it.copy(interests = next, interestsError = false)
                } else if (next.size < 5) {
                    next.add(event.name)
                    it.copy(interests = next, interestsError = false)
                } else {
                    it.copy(interestsError = true)
                }
            }
            is SignupEvent.PhotoSelected -> selectPhoto(event.uri)
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
        // Steps with async uniqueness checks (email = step 2, nickname
        // = step 6) don't advance inline; they fire the check and only
        // move on when the check confirms the value is free. Mirrors
        // iOS `SignupViewModel.validateNickName()` / the email
        // uniqueness check on `SignupEmailView`.
        when (current.step) {
            2 -> { checkEmailUniqueness(); return }
            6 -> { checkNicknameUniqueness(); return }
        }
        advanceToNextStep()
    }

    /**
     * Bump the wizard to the next step. Called by [advance] for the
     * non-uniqueness-checked steps, and by [checkEmailUniqueness] /
     * [checkNicknameUniqueness] once the async check confirms the
     * value is free. Splitting it out avoids an infinite loop where
     * the post-check `advance()` call re-enters the same step and
     * re-fires the check.
     */
    private fun advanceToNextStep() {
        val current = _state.value
        val next = current.copy(step = current.step + 1)
        _state.update { next }
        persist(next)
    }

    /**
     * Asynchronously check whether [SignupWizardState.email] is already
     * registered. On hit, sets `emailCheck` to `Error` and surfaces
     * `emailAlreadyInUse` so the email step's UI can show the error.
     * On miss, advances to step 3. Mirrors iOS
     * `FireStoreServiceManager.checkIfEmailExist`.
     */
    private fun checkEmailUniqueness() {
        val email = _state.value.email.trim()
        if (email.isEmpty()) return
        _state.update { it.copy(emailCheck = AsyncValue.Loading) }
        viewModelScope.launch {
            val result = authRepository.checkIfEmailExists(email)
            when (result) {
                is AsyncValue.Success -> {
                    if (result.data) {
                        _state.update {
                            it.copy(
                                emailCheck = AsyncValue.Error(
                                    AuthException(
                                        com.kornerz.core.network.auth.AuthError.EmailAlreadyInUse,
                                        "This email is already registered",
                                    ),
                                ),
                                emailAlreadyInUse = true,
                            )
                        }
                    } else {
                        _state.update { it.copy(emailCheck = AsyncValue.Success(Unit)) }
                        advanceToNextStep()
                    }
                }
                is AsyncValue.Error -> {
                    _state.update { it.copy(emailCheck = result) }
                }
                AsyncValue.Loading -> Unit
            }
        }
    }

    /**
     * Asynchronously check whether [SignupWizardState.nickname] is
     * already taken. On hit, sets `nicknameCheck` to `Error` and
     * populates `nicknameError` so the nickname step's UI can show the
     * error. On miss, advances to step 7. Mirrors iOS
     * `SignupViewModel.validateNickName()`.
     */
    private fun checkNicknameUniqueness() {
        val nickname = _state.value.nickname.trim()
        if (nickname.isEmpty()) return
        _state.update { it.copy(nicknameCheck = AsyncValue.Loading) }
        viewModelScope.launch {
            val result = authRepository.checkIfNickNameExists(nickname)
            when (result) {
                is AsyncValue.Success -> {
                    if (result.data) {
                        _state.update {
                            it.copy(
                                nicknameCheck = AsyncValue.Error(
                                    AuthException(
                                        com.kornerz.core.network.auth.AuthError.NicknameAlreadyInUse,
                                        "This nickname is already taken",
                                    ),
                                ),
                                nicknameError = "This nickname is already taken",
                            )
                        }
                    } else {
                        _state.update { it.copy(nicknameCheck = AsyncValue.Success(Unit)) }
                        advanceToNextStep()
                    }
                }
                is AsyncValue.Error -> {
                    _state.update { it.copy(nicknameCheck = result) }
                }
                AsyncValue.Loading -> Unit
            }
        }
    }

    /**
     * Advance to the next step bypassing the per-step validation gate.
     * Only the phone (step 3) and photo (step 7) steps expose a Skip
     * button in iOS, so the handler is a no-op everywhere else.
     *
     * Skipping the photo step drops the selection, so the account is
     * created without an avatar and no upload runs on the final step.
     */
    private fun skipStep() {
        val current = _state.value
        if (current.step != 3 && current.step != 7) return
        if (current.step == 7) {
            // The photo is dropped, so the cached copy is deleted and
            // the account is created without an avatar.
            val cached = current.photoUri
            val next = current.copy(
                step = current.step + 1,
                photoUri = null,
                photoUrl = null,
                photoError = null,
            )
            _state.update { next }
            persist(next)
            viewModelScope.launch { imageDecoder.discardCached(cached) }
            return
        }
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

    /**
     * Registers the account.
     *
     * The picked profile photo is uploaded **first** and the resulting
     * URL is carried into the registration payload — mirroring iOS,
     * where `signup()` uploads the avatar and only then calls
     * `userRepository.registerUser(newUser)`
     * (`kornerz/Kornerz/SwiftUI/OnBoarding/Signup/SignupViewModel.swift:174-208`).
     * A failed upload aborts the registration with a visible error
     * rather than silently creating an account with no avatar.
     */
    private fun submit() {
        val current = _state.value
        if (!current.isPasswordValid) return
        _state.update { it.copy(submit = AsyncValue.Loading) }
        viewModelScope.launch {
            // The upload runs first and its failure short-circuits the
            // registration. `authRepository.signup` never throws — it
            // returns an `AsyncValue.Error` — so the upload is the only
            // step that needs a `runCatching` here.
            val photoUrl = runCatching { uploadPhoto() }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            submit = AsyncValue.Error(error),
                            photoError = error.message ?: "Could not upload the selected photo",
                        )
                    }
                }
                .getOrNull()
                ?: return@launch

            _state.update { it.copy(photoUrl = photoUrl.takeIf(String::isNotEmpty)) }

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
                    // The URL of the avatar uploaded just above. It
                    // is written to the user's `firebaseImageUrl`.
                    photoUrl = photoUrl.takeIf(String::isNotEmpty),
                    // The same code is used for the Storage path
                    // (`users/{userCode}/profile/...`) so the avatar
                    // and the Firestore document agree.
                    userCode = _state.value.userCode,
                ),
            )
            _state.update {
                val isEmailAlreadyInUse = (result as? AsyncValue.Error)
                    ?.throwable?.message?.contains("already registered") == true
                it.copy(
                    submit = result,
                    emailAlreadyInUse = isEmailAlreadyInUse,
                    // If signup failed because the email is already
                    // taken, surface that as the email-step error so
                    // the user can fix it without restarting.
                    emailCheck = if (isEmailAlreadyInUse) {
                        AsyncValue.Error(
                            (result as AsyncValue.Error).throwable,
                        )
                    } else {
                        it.emailCheck
                    },
                )
            }
            if (result is AsyncValue.Success) {
                // The avatar now lives in Cloud Storage, so the local
                // copy is no longer needed.
                imageDecoder.discardCached(_state.value.photoUri)
                _navEvents.trySend(SignupNavEvent.NavigateToShell)
            }
        }
    }

    /**
     * Takes a newly picked photo and copies it into the app's private
     * cache.
     *
     * The Photo Picker hands back a content URI whose read grant is
     * short-lived, but the wizard holds the selection until the final
     * step, where the upload runs — by then the grant may have
     * expired. Copying the bytes at selection time gives the wizard a
     * `file://` URI it fully controls, so the upload always has
     * readable source bytes.
     *
     * The upload itself is deferred to the final step, where it runs
     * immediately before the account is registered — mirroring iOS,
     * which uploads the avatar inside `signup()`
     * (`kornerz/Kornerz/SwiftUI/OnBoarding/Signup/SignupViewModel.swift:174-208`).
     */
    private fun selectPhoto(sourceUri: Uri) {
        viewModelScope.launch {
            val previous = _state.value.photoUri
            val copied = runCatching { imageDecoder.copyToCache(sourceUri) }
            copied.exceptionOrNull()?.let { error ->
                _state.update {
                    it.copy(
                        photoUri = null,
                        photoUrl = null,
                        photoError = error.message ?: "Could not read the selected photo",
                    )
                }
                persist(_state.value)
                // The failed pick replaced the previous selection, so
                // its cached copy is no longer referenced.
                imageDecoder.discardCached(previous)
                return@launch
            }
            val localUri = copied.getOrThrow()
            _state.update {
                it.copy(
                    photoUri = localUri,
                    // A new pick invalidates the previous upload
                    // result and any error from it.
                    photoUrl = null,
                    photoError = null,
                    // The Storage path is keyed by the user's code,
                    // so the code is generated as soon as a photo
                    // exists. iOS does the same.
                    userCode = it.userCode ?: generateUserCode(),
                )
            }
            persist(_state.value)
            // The replaced copy is no longer needed.
            if (previous != localUri) imageDecoder.discardCached(previous)
        }
    }

    /**
     * Uploads the picked profile photo and returns its public download
     * URL, or an empty string when no photo was picked.
     *
     * This runs on the **final** step, inside [submit], immediately
     * before the account is written to the database — mirroring iOS,
     * where the avatar is uploaded inside `signup()` and a failed
     * upload aborts the registration
     * (`kornerz/Kornerz/SwiftUI/OnBoarding/Signup/SignupViewModel.swift:174-208`).
     *
     * The upload path is `users/{userCode}/profile/{uuid}.jpg`, where
     * `userCode` is the user's `codeUser` value. The code is generated
     * when the photo is picked, so the Storage object and the Firestore
     * document agree.
     *
     * @throws Throwable the upload failure — the caller turns it into
     *   the `submit` error the password step renders.
     */
    private suspend fun uploadPhoto(): String {
        val uri = _state.value.photoUri ?: return ""
        val userCode = _state.value.userCode
            ?: generateUserCode().also { code -> _state.update { it.copy(userCode = code) } }
        val bitmap = imageDecoder.decode(uri)
        return photoStorage.uploadAvatar(userCode, bitmap).getOrThrow()
    }

    /**
     * Generates the user's `codeUser` value.
     *
     * iOS uses `AppHelper.createCodeForTheUser()`
     * (`kornerz/Kornerz/Helper/AppHelper.swift:198-201`): ten characters
     * drawn from `A-Z0-9`. Android's Firestore layer generates a
     * lowercase eight-character code; this mirrors the iOS format so
     * both clients produce the same shape.
     */
    private fun generateUserCode(): String =
        (1..USER_CODE_LENGTH).map { USER_CODE_ALPHABET.random() }.joinToString("")

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
        savedStateHandle[KEY_USER_CODE] = state.userCode
        savedStateHandle[KEY_PHOTO_URL] = state.photoUrl
        // The picked photo is a content URI, which `SavedStateHandle`
        // cannot hold directly — it is persisted as a string and parsed
        // back on restore. The selection outlives the wizard's process
        // because the upload happens on the final step.
        savedStateHandle[KEY_PHOTO_URI] = state.photoUri?.toString()
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
            userCode = savedStateHandle.get<String>(KEY_USER_CODE),
            photoUrl = savedStateHandle.get<String>(KEY_PHOTO_URL),
            photoUri = savedStateHandle.get<String>(KEY_PHOTO_URI)?.let(Uri::parse),
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
        const val KEY_USER_CODE = "signup.userCode"
        const val KEY_PHOTO_URL = "signup.photoUrl"
        const val KEY_PHOTO_URI = "signup.photoUri"

        /** Mirrors iOS `AppHelper.createCodeForTheUser()` — 10 characters. */
        const val USER_CODE_LENGTH = 10

        /** Mirrors iOS `AppHelper.createCodeForTheUser()` — `A-Z0-9`. */
        const val USER_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    }
}
