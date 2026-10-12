package com.kornerz.feature.loops.comment

data class CreateCommentState(
    val text: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
)
