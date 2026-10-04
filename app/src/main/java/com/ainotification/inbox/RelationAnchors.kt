package com.ainotification.inbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Resolve live, unclipped node bounds in the canvas's own coordinate system. */
internal class RelationAnchors {
    private var canvas: LayoutCoordinates? = null
    private val nodes = mutableMapOf<String, LayoutCoordinates>()
    private var revision by mutableIntStateOf(0)

    fun canvasModifier() = Modifier.onGloballyPositioned {
        canvas = it
        revision++
    }

    fun node(id: String) = Modifier.onGloballyPositioned {
        nodes[id] = it
        revision++
    }

    // Read only during drawing: placement changes invalidate drawing, not layout/composition.
    fun bounds(id: String): Rect? {
        if (revision == 0) return null
        val frame = canvas?.takeIf { it.isAttached } ?: return null
        val node = nodes[id]?.takeIf { it.isAttached } ?: return null
        return frame.localBoundingBoxOf(node, clipBounds = false)
    }
}
