package com.kornerz.core.model

/**
 * Loading/success/error wrapper for repository results rendered by Compose.
 *
 * Every screen that needs to display data fetched asynchronously exposes
 * a `AsyncValue<T>` field on its UiState (architecture §2.2). Composables
 * pattern-match exhaustively on `Loading` / `Success` / `Error` to render
 * one of three layouts — no `isLoading` booleans, no try/catch ladders.
 */
sealed interface AsyncValue<out T> {

    data object Loading : AsyncValue<Nothing>

    data class Success<T>(val data: T) : AsyncValue<T>

    data class Error(val throwable: Throwable) : AsyncValue<Nothing>
}