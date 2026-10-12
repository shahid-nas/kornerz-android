package com.kornerz.feature.loops.reply

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ReplyScreen(
    modifier: Modifier = Modifier,
    viewModel: ReplyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val popRequested = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                ReplyNavEvent.Pop -> popRequested.value = true
                is ReplyNavEvent.ShowError -> { /* surface via snackbar in shell */ }
            }
        }
    }
    if (popRequested.value) {
        BackHandler(enabled = true) { /* no-op; shell NavController will pop */ }
    }
    ReplyContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier.fillMaxSize(),
    )
}
