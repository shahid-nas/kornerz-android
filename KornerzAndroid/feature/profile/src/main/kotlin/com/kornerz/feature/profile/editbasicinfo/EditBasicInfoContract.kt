package com.kornerz.feature.profile.editbasicinfo

import com.kornerz.core.model.EditableField
import com.kornerz.core.model.User

/**
 * UI state for the Edit Basic Info screen (P6, screen 7.2). Lists
 * five rows: NAME, EMAIL, PHONE, NICKNAME, PASSWORD — only NAME and
 * NICKNAME expose the "Change" affordance (mirrors iOS line 119).
 */
data class EditBasicInfoState(
    val user: User? = null,
    val rows: List<EditableField> = listOf(
        EditableField.NAME,
        EditableField.EMAIL,
        EditableField.PHONE,
        EditableField.NICKNAME,
        EditableField.PASSWORD,
    ),
)

sealed interface EditBasicInfoEvent {
    data class UserChanged(val user: User) : EditBasicInfoEvent
}

sealed interface EditBasicInfoNavEvent {
    data class OpenChangeScreen(val field: EditableField) : EditBasicInfoNavEvent
    data object Back : EditBasicInfoNavEvent
}
