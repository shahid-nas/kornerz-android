package com.kornerz.feature.videocall.ui

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.opentok.android.Publisher
import com.opentok.android.Subscriber

/**
 * `AndroidView` wrapper around Vonage `Publisher.view` per arch §1
 * and `project-brief.md` decision 1 ("Compose-first, with
 * `AndroidView` wrappers only where vendored SDKs force it").
 * Mirrors iOS `PublisherContainer.swift`.
 */
@Composable
fun PublisherContainer(
    publisher: Publisher?,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            val parent = ctx.publisherParent()
            if (publisher != null) {
                val view = publisher.view
                parent.removeAllViews()
                parent.addView(view)
            }
            parent
        },
        update = { parent ->
            parent.removeAllViews()
            publisher?.view?.let { parent.addView(it) }
        },
        modifier = modifier,
    )
}

private fun Context.publisherParent(): ViewGroup {
    val parent = android.widget.FrameLayout(this)
    parent.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    return parent
}

/**
 * `AndroidView` wrapper around Vonage `Subscriber.view`. Mirrors
 * iOS `SubscriberContainer.swift`.
 */
@Composable
fun SubscriberContainer(
    subscriber: Subscriber?,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            val parent = android.widget.FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            if (subscriber != null) parent.addView(subscriber.view)
            parent
        },
        update = { parent ->
            parent.removeAllViews()
            subscriber?.view?.let { parent.addView(it) }
        },
        modifier = modifier,
    )
}