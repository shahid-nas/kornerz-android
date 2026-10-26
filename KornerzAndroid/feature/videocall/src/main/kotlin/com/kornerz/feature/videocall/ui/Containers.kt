package com.kornerz.feature.videocall.ui

import android.content.Context
import android.util.Log
import android.graphics.Outline
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.opentok.android.BaseVideoRenderer
import com.opentok.android.Publisher
import com.opentok.android.Subscriber

private const val TAG = "VideoCallUI"

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
    cornerRadius: Dp = 0.dp,
) {
    AndroidView(
        factory = { ctx ->
            val parent = ctx.videoParent(cornerRadius)
            publisher?.view?.let {
                it.fillTile(publisher.renderer)
                parent.attach(it)
            }
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
                if (desired != null) {
                    desired.fillTile(publisher.renderer)
                    parent.attach(desired)
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Attaches the SDK's video view to the container.
 *
 * The SDK hands out a single `View` instance that it keeps attached to
 * its own internal parent. Adding it to our container while it still
 * has that parent throws
 * `IllegalStateException: The specified child already has a parent`.
 * Detaching it first makes the attach safe no matter who owned it.
 */
private fun FrameLayout.attach(child: View) {
    (child.parent as? ViewGroup)?.removeView(child)
    addView(child)
}

/**
 * The host of the SDK's video view.
 *
 * The SDK hands out its renderer as a plain `View`, and a `View` added
 * to a `FrameLayout` without layout params inherits the group's default
 * `WRAP_CONTENT` — so it measured to its natural size instead of
 * filling the tile. The result was a black card with a thin strip of
 * video along its bottom edge.
 *
 * The child therefore gets explicit `MATCH_PARENT` params, and the
 * group is a `FrameLayout` so the video is positioned at the top left
 * of the tile, exactly where it is drawn.
 */
private fun Context.videoParent(cornerRadius: Dp): FrameLayout = FrameLayout(this).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    if (cornerRadius > 0.dp) {
        // The video surface is clipped natively, by the platform, rather
        // than by a Compose `Modifier.clip`.
        //
        // A `TextureView` renders its content from a `SurfaceTexture`,
        // i.e. from a separate buffer that the Compose layer's clip
        // does not shape. The Compose clip therefore left the corners
        // of the surface uncut and a thin translucent black fringe
        // around the video. `clipToOutline` with a rounded outline
        // clips the surface itself.
        val radiusPx = cornerRadius.value * resources.displayMetrics.density
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
    }
}

/**
 * `AndroidView` wrapper around Vonage `Subscriber.view`. Mirrors
 * iOS `SubscriberContainer.swift`.
 *
 * The SDK renders the video through a `TextureView` (see
 * `TextureViewSessionOptions`), which only draws frames once it has
 * been resumed. The resume is driven by `VonageSessionController`
 * (see `resumeRenderers`) rather than from here: the session's own
 * pause/resume cycle has to stay authoritative, otherwise a
 * backgrounded call would keep drawing.
 */
@Composable
fun SubscriberContainer(
    subscriber: Subscriber?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
) {
    AndroidView(
        factory = { ctx ->
            val parent = ctx.videoParent(cornerRadius)
            subscriber?.view?.let {
                it.fillTile(subscriber.renderer)
                parent.attach(it)
            }
            parent
        },
        update = { parent ->
            val desired = subscriber?.view
            val current = parent.getChildAt(0)
            if (current !== desired) {
                parent.removeAllViews()
                if (desired != null) {
                    desired.fillTile(subscriber.renderer)
                    parent.attach(desired)
                }
            }
        },
        modifier = modifier,
    )
}

/**
 * Makes the SDK's video view fill the tile it is placed in.
 *
 * Two things are needed for the video to cover the whole tile:
 *
 *  1. **The view has to be laid out at the tile's size.** The SDK's
 *     view is added to a `FrameLayout` without layout params, so it
 *     inherited `WRAP_CONTENT` and measured to the video's own
 *     resolution. The video was then drawn at that size and left the
 *     rest of the tile black.
 *
 *  2. **The video has to be scaled to fill rather than to fit.**
 *     The SDK renders pillar-boxed by default (`isPillarBoxEnabled`),
 *     which letterboxes the video inside the surface. iOS asks for
 *     the opposite — `OTSubscriber.viewScaleBehavior = .fill` — and
 *     the Android equivalent is `STYLE_VIDEO_FILL`.
 */
private fun View.fillTile(renderer: BaseVideoRenderer?) {
    layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
        Gravity.TOP or Gravity.START,
    )
    if (renderer != null) {
        renderer.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL)
    }
    // The view has to end up at the tile's size and the video has to
    // fill it. When either is wrong the tile shows a black card with
    // the video squeezed into a strip, so the measured size of the
    // view *and* of its parent are logged once the layout has settled.
    if (loggedViews.add(this)) {
        addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
            val parent = view.parent as? View
            Log.i(
                TAG,
                "video view laid out: ${view.width}x${view.height} at ($left,$top)-($right,$bottom) " +
                    "parent=${parent?.width}x${parent?.height} " +
                    "layoutParams=${view.layoutParams?.width}x${view.layoutParams?.height}",
            )
            view.syncSurfaceSize()
        }
    }
    startSurfaceSizeSync()
}

/**
 * Keeps the `TextureView`'s surface buffer the same size as the view.
 *
 * The SDK draws each frame with the *surface* size as its GL viewport.
 * A `TextureView` keeps whatever buffer size it was given, so when the
 * tile is laid out at a different size than the surface, the frame is
 * drawn into only a part of the buffer and the rest of the tile keeps
 * the cleared (transparent) surface — the thin dark band that was
 * visible around the subscriber's video.
 *
 * Setting the default buffer size to the view's size makes the
 * surface and the viewport agree, so the video covers the tile edge to
 * edge.
 */
private fun View.syncSurfaceSize() {
    if (width <= 0 || height <= 0) return
    val texture = this as? TextureView ?: return
    val surface = texture.surfaceTexture ?: return
    surface.setDefaultBufferSize(width, height)
}

/**
 * Re-applies the surface size while the view settles.
 *
 * The SDK owns the `SurfaceTextureListener` of the `TextureView`, so
 * the buffer size cannot be set from a listener of our own — the
 * surface is only available once the SDK's own callback has run, and
 * it is recreated every time the view is attached. The size is
 * therefore re-applied for a short while after every attach.
 */
private fun View.startSurfaceSizeSync() {
    val texture = this as? TextureView ?: return
    var attempts = 0
    val sync = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow) return
            syncSurfaceSize()
            if (++attempts < 20) {
                postDelayed(this, 100)
            }
        }
    }
    postDelayed(sync, 100)
}

private val loggedViews = java.util.Collections.newSetFromMap(
    java.util.WeakHashMap<View, Boolean>(),
)
