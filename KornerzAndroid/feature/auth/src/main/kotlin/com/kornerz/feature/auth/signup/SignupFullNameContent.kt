package com.kornerz.feature.auth.signup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.kornerz.core.ui.theme.KornerzTheme

/**
 * Stateless renderer for the signup wizard's full-name step. Mirrors the
 * iOS `SignupFullNameView` (kornerz/.../Signup/Views/SignupFullNameView.swift)
 * 1:1 using the shared [BrandedSignupScaffold]. The CTA is bound to
 * [SignupWizardState.isFullNameValid] (which mirrors the iOS
 * `SignupViewModel.isFullNameValid` regex).
 */
@Composable
fun SignupFullNameContent(
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BrandedSignupScaffold(
        title = "What is your name?",
        onBack = onBack,
        modifier = modifier,
    ) {
        BrandedTextField(
            value = state.fullName,
            onValueChange = { onEvent(SignupEvent.FullNameChanged(it)) },
            placeholder = "Full name",
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        )
        BlackCapsuleButton(
            text = "Next",
            onClick = { onEvent(SignupEvent.Continue) },
            enabled = state.isFullNameValid,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignupFullNameEmptyPreview() {
    KornerzTheme(darkTheme = false) {
        SignupFullNameContent(
            state = SignupWizardState(),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignupFullNameFilledPreview() {
    KornerzTheme(darkTheme = false) {
        SignupFullNameContent(
            state = SignupWizardState(fullName = "Anne-Marie O'Connor"),
            onEvent = {},
            onBack = {},
        )
    }
}
