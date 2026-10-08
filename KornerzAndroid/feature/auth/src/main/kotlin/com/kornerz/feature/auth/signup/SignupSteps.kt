@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.kornerz.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kornerz.core.model.AsyncValue
import com.kornerz.core.model.Country
import com.kornerz.core.model.KornerInterest
import com.kornerz.core.storage.country.CountryCodesRepository
import com.kornerz.core.storage.interests.KornerInterestRepository
import com.kornerz.core.ui.components.AsyncValueContent
import com.kornerz.core.ui.components.KornerzAvatar
import com.kornerz.core.ui.components.KornerzButton
import com.kornerz.core.ui.components.KornerzOtpTextField
import com.kornerz.core.ui.components.KornerzPasswordField
import com.kornerz.core.ui.components.KornerzTextField
import com.kornerz.core.ui.components.rememberKornerzImagePicker
import com.kornerz.core.ui.theme.Spacing

// ─────────────────────────────────────────────────────────────────────────
// Step composables
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun SignupFullNameStep(state: SignupWizardState, onEvent: (SignupEvent) -> Unit) {
    KornerzTextField(
        value = state.fullName,
        onValueChange = { onEvent(SignupEvent.FullNameChanged(it)) },
        label = "Full name",
        supportingText = "First and last name",
    )
}

@Composable
fun SignupNicknameStep(state: SignupWizardState, onEvent: (SignupEvent) -> Unit) {
    KornerzTextField(
        value = state.nickname,
        onValueChange = { onEvent(SignupEvent.NicknameChanged(it)) },
        label = "Nickname",
        supportingText = "3-20 chars, letters / numbers / underscores",
    )
}

@Composable
fun SignupEmailStep(state: SignupWizardState, onEvent: (SignupEvent) -> Unit) {
    Column {
        KornerzTextField(
            value = state.email,
            onValueChange = { onEvent(SignupEvent.EmailChanged(it)) },
            label = "Email",
            keyboardType = KeyboardType.Email,
            isError = state.emailAlreadyInUse,
            errorText = if (state.emailAlreadyInUse) "This email is already registered" else null,
        )
        if (state.emailAlreadyInUse) {
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = "Sign in instead or use a different email address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SignupPasswordStep(state: SignupWizardState, onEvent: (SignupEvent) -> Unit) {
    Column {
        KornerzPasswordField(
            value = state.password,
            onValueChange = { onEvent(SignupEvent.PasswordChanged(it)) },
            label = "Password",
        )
        Spacer(Modifier.height(Spacing.space6))
        KornerzPasswordField(
            value = state.confirmPassword,
            onValueChange = { onEvent(SignupEvent.ConfirmPasswordChanged(it)) },
            label = "Confirm password",
            isError = state.confirmPassword.isNotEmpty() && state.password != state.confirmPassword,
            errorText = if (state.confirmPassword.isNotEmpty() && state.password != state.confirmPassword) {
                "Passwords do not match"
            } else null,
        )
        Spacer(Modifier.height(Spacing.space4))
        Text(
            text = "At least 6 characters. Use a unique phrase.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SignupPhoneStep(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    repository: CountryCodesRepository,
) {
    var showSheet by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space4),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedButton(
                onClick = { showSheet = true },
                modifier = Modifier.widthIn(min = 96.dp),
            ) {
                Text(
                    text = state.countryDialCode,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            KornerzTextField(
                value = state.phone,
                onValueChange = { onEvent(SignupEvent.PhoneChanged(it)) },
                label = "Phone number",
                keyboardType = KeyboardType.Phone,
                modifier = Modifier.weight(1f),
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
                .padding(horizontal = Spacing.space8),
        ) {
            Text(
                text = "Select country code",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.space4))
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
                                .selectable(
                                    selected = selected,
                                    onClick = { onSelect(country) },
                                )
                                .padding(vertical = Spacing.space4),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = country.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = country.dialCode,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.space8))
        }
    }
}

@Composable
fun SignupLanguageStep(state: SignupWizardState, onEvent: (SignupEvent) -> Unit) {
    val languages = listOf(
        "en" to "English",
        "pt-BR" to "Portuguese (Brazil)",
    )
    Column {
        for ((code, label) in languages) {
            val selected = state.language == code
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = { onEvent(SignupEvent.LanguageChanged(code)) },
                    ),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.space8),
                )
            }
        }
    }
}

@Composable
fun SignupInterestsStep(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    repository: KornerInterestRepository,
) {
    var loaded by remember { mutableStateOf<AsyncValue<List<KornerInterest>>>(AsyncValue.Loading) }
    LaunchedEffect(Unit) {
        loaded = repository.getAll().fold(
            onSuccess = { AsyncValue.Success(it) },
            onFailure = { AsyncValue.Error(it) },
        )
    }
    AsyncValueContent(value = loaded, onRetry = { loaded = AsyncValue.Loading }) { items ->
        Column {
            Text(
                text = "Pick 1 to 5 favorites",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.space4))
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.space4),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.space4),
            ) {
                items.forEach { interest ->
                    val selected = state.interests.contains(interest.slug)
                    FilterChip(
                        selected = selected,
                        onClick = { onEvent(SignupEvent.InterestToggled(interest.slug)) },
                        label = { Text(interest.title) },
                    )
                }
            }
            if (state.interests.size >= 5) {
                Spacer(Modifier.height(Spacing.space4))
                Text(
                    text = "You can pick up to 5 favorites",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun SignupPhotoStep(state: SignupWizardState, onEvent: (SignupEvent) -> Unit) {
    val picker = rememberKornerzImagePicker { uri ->
        onEvent(SignupEvent.PhotoSelected(uri))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        KornerzAvatar(previewUri = state.photoUri)
        Spacer(Modifier.height(Spacing.space6))
        KornerzButton(
            text = "Choose from gallery",
            onClick = {
                picker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContractsPickImage,
                    ),
                )
            },
            style = com.kornerz.core.ui.components.KornerzButtonStyle.Outlined,
        )
        if (state.photoUpload is AsyncValue.Error) {
            Spacer(Modifier.height(Spacing.space4))
            Text(
                text = "Upload failed — retry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            KornerzButton(
                text = "Retry upload",
                onClick = { onEvent(SignupEvent.PhotoUploadRetry) },
                style = com.kornerz.core.ui.components.KornerzButtonStyle.Text,
            )
        }
    }
}

private val ActivityResultContractsPickImage =
    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly