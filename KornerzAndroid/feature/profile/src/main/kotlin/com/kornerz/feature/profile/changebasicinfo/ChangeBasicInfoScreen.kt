package com.kornerz.feature.profile.changebasicinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChangeBasicInfoScreen(
    onNavigateToSuccess: (String) -> Unit,
    onShowError: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ChangeBasicInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is ChangeBasicInfoNavEvent.UpdatedSuccessfully -> onNavigateToSuccess(event.message)
                is ChangeBasicInfoNavEvent.ShowError -> onShowError(event.message)
                ChangeBasicInfoNavEvent.Back -> onBack()
            }
        }
    }
    ChangeBasicInfoDialog(
        state = state,
        onDraftChange = { viewModel.onEvent(ChangeBasicInfoEvent.DraftValueChanged(it)) },
        onSave = { viewModel.onEvent(ChangeBasicInfoEvent.Save) },
        onDismiss = onBack,
    )
}
