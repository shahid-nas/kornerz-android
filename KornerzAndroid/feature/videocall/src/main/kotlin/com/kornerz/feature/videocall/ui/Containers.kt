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
            publisher?.view?.let { parent.addView(it) }
            parent
        },
        update = { parent ->
            // Only re-attach when the rendered view actually changed —
            // detaching and re-adding the SDK's surface on every
            // recomposition restarts the video renderer.
            val desired = publisher?.view
            val current = parent.getChildAt(0)
            if (current !== desired) {
                parent.removeAllViews()
                if (desired != null) parent.addView(desired)
            }
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
            subscriber?.view?.let { parent.addView(it) }
            parent
        },
        update = { parent ->
            val desired = subscriber?.view
            val current = parent.getChildAt(0)
            if (current !== desired) {
                parent.removeAllViews()
                if (desired != null) parent.addView(desired)
            }
        },
        modifier = modifier,
    )
}
