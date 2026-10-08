package com.kornerz.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kornerz.core.ui.components.KornerzButton
import com.kornerz.core.ui.theme.Spacing

/**
 * Shared scaffold used by every wizard step (architecture §8.4). Provides
 * a header (step title + step count), a Back control (disabled on step
 * 1), and a Continue / Create account control on the final step.
 */
@Composable
fun SignupStepScaffold(
    title: String,
    stepNumber: Int,
    totalSteps: Int,
    state: SignupWizardState,
    onEvent: (SignupEvent) -> Unit,
    continueLabel: String = "Continue",
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.space8, vertical = Spacing.space12),
        ) {
            Text(
                text = "Step $stepNumber of $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.space2))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Spacing.space8))

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
            ) {
                content()
            }

            Spacer(Modifier.height(Spacing.space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (stepNumber > 1) {
                    TextButton(onClick = { onEvent(SignupEvent.Back) }) {
                        Text("Back")
                    }
                } else {
                    Spacer(Modifier.height(Spacing.space4))
                }
                KornerzButton(
                    text = continueLabel,
                    onClick = {
                        if (stepNumber == totalSteps) {
                            onEvent(SignupEvent.Submit)
                        } else {
                            onEvent(SignupEvent.Continue)
                        }
                    },
                    enabled = state.canContinue,
                    modifier = Modifier.weight(1f).padding(start = Spacing.space4),
                )
            }
        }
    }
}