package com.kornerz.feature.loops.comment

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kornerz.feature.loops.comment.CreateCommentNavEvent

@Composable
fun CreateCommentScreen(
    modifier: Modifier = Modifier,
    viewModel: CreateCommentViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dismissRequested by remember { mutableStateOf(false) }
    
    LaunchedEffect(viewModel) {
        viewModel.navEvents.collect { event ->
            when (event) {
                CreateCommentNavEvent.Dismiss -> {
                    dismissRequested = true
                    onDismiss()
                }
                is CreateCommentNavEvent.OnError -> { /* inline error already shown */ }
            }
        }
    }
    
    BackHandler(enabled = true) {
        viewModel.dismiss()
    }
    
    CreateCommentContentNew(
        state = state,
        onEvent = viewModel::onEvent,
        viewModel = viewModel,
        modifier = modifier,
    )
}
