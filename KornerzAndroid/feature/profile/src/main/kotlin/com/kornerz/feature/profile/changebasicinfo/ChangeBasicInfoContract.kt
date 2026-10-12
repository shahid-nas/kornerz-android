package com.kornerz.feature.profile.changebasicinfo

import com.kornerz.core.model.EditableField

/**
 * UI state for the single-field change modal/sheet (P6, screen 7.3).
 * Mirrors iOS `ChangeBasicInfoViewModel`.
 */
data class ChangeBasicInfoState(
    val field: EditableField = EditableField.NAME,
    val originalValue: String = "",
    val draftValue: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
) {
    val canSave: Boolean
        get() = draftValue.isNotEmpty() && draftValue != originalValue && !isSaving
}

sealed interface ChangeBasicInfoEvent {
    data class DraftValueChanged(val value: String) : ChangeBasicInfoEvent
    data object Save : ChangeBasicInfoEvent
    data object DismissError : ChangeBasicInfoEvent
    data object ResetSuccess : ChangeBasicInfoEvent
}

sealed interface ChangeBasicInfoNavEvent {
    data class UpdatedSuccessfully(val message: String) : ChangeBasicInfoNavEvent
    data class ShowError(val message: String) : ChangeBasicInfoNavEvent
    data object Back : ChangeBasicInfoNavEvent
}
