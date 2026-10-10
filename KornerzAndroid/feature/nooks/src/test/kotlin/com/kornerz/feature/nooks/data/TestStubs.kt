package com.kornerz.feature.nooks.data

import com.google.firebase.firestore.DocumentSnapshot
import com.kornerz.core.model.Korner
import com.kornerz.core.model.Nook
import com.kornerz.core.model.UpcomingNookItem
import com.kornerz.core.model.User
import com.kornerz.core.network.nooks.NookFirestoreApi
import io.mockk.mockk

object NookFirestoreApiPageStub {
    fun kornerPage(
        items: List<Korner> = emptyList(),
        lastSnapshot: DocumentSnapshot? = null,
        hasMore: Boolean = false,
    ): NookFirestoreApi.KornerPage = NookFirestoreApi.KornerPage(
        items = items,
        lastSnapshot = lastSnapshot,
        hasMore = hasMore,
    )

    fun upcoming(
        items: List<Nook> = emptyList(),
        lastSnapshot: DocumentSnapshot? = null,
        hasMore: Boolean = false,
    ): NookFirestoreApi.NookPage = NookFirestoreApi.NookPage(
        items = items,
        lastSnapshot = lastSnapshot,
        hasMore = hasMore,
    )
}

object NooksUpcomingPageStub {
    fun upcoming(
        items: List<UpcomingNookItem> = emptyList(),
        lastSnapshot: DocumentSnapshot? = null,
        hasMore: Boolean = false,
    ): NooksUpcomingPage = NooksUpcomingPage(
        items = items,
        lastSnapshot = lastSnapshot,
        hasMore = hasMore,
    )
}

internal fun stubUser(code: String) = User(
    id = code,
    email = "$code@example.com",
    displayName = "User $code",
    nickname = code,
    userCode = code,
)
