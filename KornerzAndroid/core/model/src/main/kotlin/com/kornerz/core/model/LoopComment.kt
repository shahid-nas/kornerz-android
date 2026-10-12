package com.kornerz.core.model

import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Canonical loop-comment record. Mirrors the iOS `LoopComment` struct
 * (`kornerz/Kornerz/Models/LoopComment.swift`) field-for-field, including
 * the `parentId` null-encoding behavior: when `parentId` is `null` the
 * iOS code calls `encodeNil(forKey: .parentId)` (see `LoopComment.swift:60-65`),
 * so the field is present in the document with an explicit `null` value
 * — not omitted.
 *
 * The Android port keeps `parentId` default-encoded as `null` so the
 * Firestore shape is identical to the iOS shape, regardless of the
 * serialization layer used to write the document.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class LoopComment(
    @SerialName("id")
    @EncodeDefault
    val id: String? = null,
    @SerialName("loopId")
    @EncodeDefault
    val loopId: String? = null,
    @SerialName("parentId")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val parentId: String? = null,
    @SerialName("userId")
    @EncodeDefault
    val userId: String? = null,
    @SerialName("active")
    @EncodeDefault
    val active: Boolean? = true,
    @SerialName("comment")
    @EncodeDefault
    val comment: String? = null,
    @SerialName("creationDate")
    @EncodeDefault
    val creationDate: Instant? = null,
    @SerialName("repliesCount")
    @EncodeDefault
    val repliesCount: Int? = null,
    @SerialName("likesCount")
    @EncodeDefault
    val likesCount: Int? = null,
)
