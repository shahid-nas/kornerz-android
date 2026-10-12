package com.kornerz.feature.loops.comment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CreateCommentScreen(
    modifier: Modifier = Modifier,
    viewModel: CreateCommentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dismissRequested = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                CreateCommentNavEvent.Dismiss -> dismissRequested.value = true
                is CreateCommentNavEvent.OnError -> { /* inline error already shown */ }
            }
        }
    }
    if (dismissRequested.value) {
        BackHandler(enabled = true) { /* no-op; NavController will pop via the dialog route */ }
    }
    Box(modifier = modifier.fillMaxSize()) {
        CreateCommentContent(state = state, onEvent = viewModel::onEvent)
    }
}
