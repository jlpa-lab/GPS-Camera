package com.gps.map.camera.and.time.stamp.ui.earthview

import earth.worldwind.BasicWorldWindowController
import earth.worldwind.WorldWindow

/** Keeps pinch zoom above the useful native resolution of the imagery layer. */
class EarthViewController(
    worldWindow: WorldWindow,
    private val minimumRangeMeters: Double,
) : BasicWorldWindowController(worldWindow) {

    override fun applyChanges() {
        lookAt.range = lookAt.range.coerceAtLeast(minimumRangeMeters)
        super.applyChanges()
    }
}
