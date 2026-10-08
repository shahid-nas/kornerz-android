@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.kornerz.feature.auth.signup

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Country
import com.kornerz.core.model.KornerInterest
import com.kornerz.core.storage.country.CountryCodesRepository
import com.kornerz.core.storage.interests.KornerInterestRepository
import com.kornerz.core.ui.components.AsyncValueContent
import com.kornerz.core.ui.components.rememberKornerzImagePicker
import com.kornerz.core.ui.theme.Satoshi

// ─── iOS-matching branded step content ────────────────────────────────────
//
// Every step below mirrors the corresponding iOS view in
// kornerz/.../Signup/Views/*.swift 1:1 — Aqua #68B6CC background, cloud
// decoration, white Satoshi title, transparent bar text field, black
// capsule "Next" CTA. All steps use the shared [BrandedSignupScaffold].

// ── Step 2 — Nickname ──────────────────────────────────────────────────────

@Composable
fun SignupNicknameStep(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    onBack: () -> Unit,
    onSuggestNickname: () -> Unit,
) {
    BrandedSignupScaffold(
        title = "Choose a nickname",
        onBack = onBack,
    ) {
        BrandedTextField(
            value = state.nickname,
            onValueChange = { onEvent(SignupEvent.NicknameChanged(it)) },
            placeholder = "Nickname",
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
        )
        if (state.nicknameError != null) {
            Text(
                text = state.nicknameError,
                color = Color.Red,
                fontFamily = Satoshi,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BorderedCapsuleButton(
                text = "Suggest nickname",
                onClick = onSuggestNickname,
                color = Color.Black,
            )
            BlackCapsuleButton(
                text = "Next",
                onClick = { onEvent(SignupEvent.Continue) },
                enabled = state.isStep2Valid,
            )
        }
    }
}

// ── Step 3 — Email ────────────────────────────────────────────────────────

@Composable
fun SignupEmailStep(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    onBack: () -> Unit,
) {
    BrandedSignupScaffold(
        title = "What is your email?",
        onBack = onBack,
    ) {
        BrandedTextField(
            value = state.email,
            onValueChange = { onEvent(SignupEvent.EmailChanged(it)) },
            placeholder = "Valid email",
            keyboardType = KeyboardType.Email,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Next,
        )
        if (state.emailAlreadyInUse) {
            Text(
                text = "This email is already registered",
                color = Color.Red,
                fontFamily = Satoshi,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        BlackCapsuleButton(
            text = "Next",
            onClick = { onEvent(SignupEvent.Continue) },
            enabled = state.isStep3Valid && !state.emailAlreadyInUse,
        )
    }
}

// ── Step 4 — Password ─────────────────────────────────────────────────────

@Composable
fun SignupPasswordStep(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    onBack: () -> Unit,
) {
    BrandedSignupScaffold(
        title = "Create a password with at least 6 digits",
        onBack = onBack,
    ) {
        BrandedPasswordField(
            value = state.password,
            onValueChange = { onEvent(SignupEvent.PasswordChanged(it)) },
            placeholder = "Password",
            imeAction = ImeAction.Next,
        )
        BrandedPasswordField(
            value = state.confirmPassword,
            onValueChange = { onEvent(SignupEvent.ConfirmPasswordChanged(it)) },
            placeholder = "Confirm password",
            imeAction = ImeAction.Done,
        )
        Text(
            text = "At least 6 characters.",
            color = Color.White.copy(alpha = 0.8f),
            fontFamily = Satoshi,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
        )
        BlackCapsuleButton(
            text = "Next",
            onClick = { onEvent(SignupEvent.Continue) },
            enabled = state.isStep4Valid,
        )
    }
}

// ── Step 5 — Phone ────────────────────────────────────────────────────────

@Composable
fun SignupPhoneStep(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    onBack: () -> Unit,
    repository: CountryCodesRepository,
) {
    var showSheet by remember { mutableStateOf(false) }

    BrandedSignupScaffold(
        title = "What is your phone number?",
        onBack = onBack,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BorderedCapsuleButton(
                text = state.countryDialCode,
                onClick = { showSheet = true },
                color = Color.White,
            )
            BrandedTextField(
                value = state.phone,
                onValueChange = { onEvent(SignupEvent.PhoneChanged(it)) },
                placeholder = "Phone Number",
                keyboardType = KeyboardType.Phone,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BorderedCapsuleButton(
                text = "Skip",
                onClick = { onEvent(SignupEvent.Continue) },
                color = Color.Black,
            )
            BlackCapsuleButton(
                text = "Next",
                onClick = { onEvent(SignupEvent.Continue) },
                enabled = state.isStep5Valid,
            )
        }
    }

    if (showSheet) {
        CountryCodePickerSheet(
            repository = repository,
            selectedDialCode = state.countryDialCode,
            onSelect = { country ->
                onEvent(SignupEvent.CountrySelected(country.dialCode, country.code))
                showSheet = false
            },
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CountryCodePickerSheet(
    repository: CountryCodesRepository,
    selectedDialCode: String,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loaded by remember { mutableStateOf<AsyncValue<List<Country>>>(AsyncValue.Loading) }

    LaunchedEffect(Unit) {
        loaded = repository.getAll().fold(
            onSuccess = { AsyncValue.Success(it) },
            onFailure = { AsyncValue.Error(it) },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "Select Country Code",
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            )
            AsyncValueContent(
                value = loaded,
                onRetry = { loaded = AsyncValue.Loading },
            ) { countries ->
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(countries, key = { it.code }) { country ->
                        val selected = country.dialCode == selectedDialCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = country.name,
                                fontFamily = Satoshi,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = if (selected) Color(0xFF5D47ED) else Color.Black,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = country.dialCode,
                                fontFamily = Satoshi,
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp,
                                color = if (selected) Color(0xFF5D47ED)
                                else Color(0xFF9E9EAE),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Step 6 — Language ─────────────────────────────────────────────────────

private val DefaultLanguages = listOf(
    "en" to "English",
    "pt-BR" to "Portuguese",
    "es" to "Spanish",
    "it" to "Italian",
    "fr" to "French",
    "de" to "German",
    "ru" to "Russian",
    "ja" to "Japanese",
    "ar" to "Arabic",
    "zh" to "Mandarin",
)

@Composable
fun SignupLanguageStep(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    onBack: () -> Unit,
) {
    val languages = remember { DefaultLanguages }
    BrandedSignupScaffold(
        title = "Which languages are you able to communicate easily?",
        onBack = onBack,
    ) {
        SelectableCapsuleGrid(
            items = languages,
            isSelected = { state.languages.contains(it.first) },
            label = { it.second },
            onToggle = { onEvent(SignupEvent.LanguageToggled(it.first)) },
            modifier = Modifier.fillMaxWidth(),
        )
        BlackCapsuleButton(
            text = "Next",
            onClick = { onEvent(SignupEvent.Continue) },
            enabled = state.isStep6Valid,
        )
    }
}

// ── Step 7 — Interests (Kornerz) ──────────────────────────────────────────

@Composable
fun SignupInterestsStep(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    onBack: () -> Unit,
    repository: KornerInterestRepository,
) {
    var loaded by remember { mutableStateOf<AsyncValue<List<KornerInterest>>>(AsyncValue.Loading) }
    LaunchedEffect(Unit) {
        loaded = repository.getAll().fold(
            onSuccess = { AsyncValue.Success(it) },
            onFailure = { AsyncValue.Error(it) },
        )
    }
    BrandedSignupScaffold(
        title = "Pick your Korners. Which topics are you interested in?",
        onBack = onBack,
    ) {
        AsyncValueContent(
            value = loaded,
            onRetry = { loaded = AsyncValue.Loading },
        ) { interests ->
            SelectableCapsuleGrid(
                items = interests,
                isSelected = { state.interests.contains(it.slug) },
                label = { it.title },
                onToggle = { onEvent(SignupEvent.InterestToggled(it.slug)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.interestsError) {
            Text(
                text = "You can pick up to 5 favorites",
                color = Color.White,
                fontFamily = Satoshi,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
            )
        }
        BlackCapsuleButton(
            text = "Next",
            onClick = { onEvent(SignupEvent.Continue) },
            enabled = state.isStep7Valid,
        )
    }
}

// ── Step 8 — Photo ────────────────────────────────────────────────────────

@Composable
fun SignupPhotoStep(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    onBack: () -> Unit,
) {
    val picker = rememberKornerzImagePicker { uri ->
        onEvent(SignupEvent.PhotoSelected(uri))
    }
    val pickImageOnly = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }
    BrandedSignupScaffold(
        title = "Add a photo",
        onBack = onBack,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            // iOS uses a 150x150 black rounded square (corner radius 32)
            // rotated 45° with "Add Photo" in the center. We replicate
            // the rounded square (without the 45° rotation so the label
            // stays readable) and tap-to-pick.
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .background(
                        color = Color.Black,
                        shape = RoundedCornerShape(32.dp),
                    )
                    .clickable { picker.launch(pickImageOnly) },
                contentAlignment = Alignment.Center,
            ) {
                val hasPhoto = state.photoUri != null
                Text(
                    text = if (hasPhoto) "Photo selected" else "Add Photo",
                    color = Color.White,
                    fontFamily = Satoshi,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BorderedCapsuleButton(
                text = "Skip",
                onClick = { onEvent(SignupEvent.Continue) },
                color = Color.Black,
            )
            BlackCapsuleButton(
                text = "Next",
                onClick = { onEvent(SignupEvent.Continue) },
                enabled = state.isStep8Valid,
            )
        }
    }
}
