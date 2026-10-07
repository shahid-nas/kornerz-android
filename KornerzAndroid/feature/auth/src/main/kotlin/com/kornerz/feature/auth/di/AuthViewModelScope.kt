package com.kornerz.feature.auth.di

import javax.inject.Qualifier

/**
 * Qualifier shared by every `@HiltViewModel` in `feature/auth`. Lets us
 * install a CoroutineScope (default: `viewModelScope`) on auth VMs in
 * the future without changing the call sites.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthViewModelScope