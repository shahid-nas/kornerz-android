package com.kornerz.core.ui.components

import androidx.compose.runtime.Composable
import com.kornerz.core.model.AsyncValue

/**
 * Render an [AsyncValue] with a single when-branch per state. Wraps the
 * boilerplate so feature Composables stay one-line:
 *
 * ```
 * AsyncValueContent(
 *     value = state.submit,
 *     onRetry = { viewModel.onEvent(LoginEvent.Submit) },
 * ) { state.submit as AsyncValue.Success -> /* render content */ }
 * ```
 */
@Composable
fun <T> AsyncValueContent(
    value: AsyncValue<T>,
    onRetry: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    loading: @Composable () -> Unit = { KornerzLoadingView(modifier) },
    error: @Composable (Throwable) -> Unit = { KornerzErrorState(it.message ?: "Something went wrong", onRetry, modifier) },
    content: @Composable (T) -> Unit,
) {
    when (value) {
        AsyncValue.Loading -> loading()
        is AsyncValue.Error -> error(value.throwable)
        is AsyncValue.Success -> content(value.data)
    }
}