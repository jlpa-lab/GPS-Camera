package com.gps.map.camera.and.time.stamp.ui.earthview

import earth.worldwind.layer.cache.TileSourceFactoryAdapter
import earth.worldwind.layer.cache.UrlTemplateImageTileSource
import earth.worldwind.layer.mercator.MercatorTiledImageLayer
import earth.worldwind.layer.mercator.MercatorTiledSurfaceImage
import earth.worldwind.layer.mercator.MercatorSector
import earth.worldwind.geom.Location
import earth.worldwind.util.LevelSet

/**
 * High-resolution imagery rendered by WorldWind's globe renderer.
 *
 * This is intentionally an XYZ layer rather than a Google tile integration. The service
 * provides cached imagery in Web Mercator and WorldWind handles the reprojection and fallback.
 */
class WorldImageryLayer : MercatorTiledImageLayer("World Imagery", createSurfaceImage()) {
    companion object {
        private const val TILE_URL =
            "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

        private const val MAX_ZOOM = 19
        private const val TILE_SIZE = 256

        private fun createSurfaceImage(): MercatorTiledSurfaceImage {
            val source = UrlTemplateImageTileSource(
                urlTemplate = TILE_URL,
                connectTimeoutMs = 7_000,
                requestTimeoutMs = 20_000
            )
            val tileFactory = TileSourceFactoryAdapter(source, "image/jpeg")
            val origin = MercatorSector()
            val levelSet = LevelSet(
                sector = MercatorSector(),
                tileOrigin = origin,
                firstLevelDelta = Location(origin.deltaLatitude, origin.deltaLongitude),
                numLevels = MAX_ZOOM + 1,
                tileWidth = TILE_SIZE,
                tileHeight = TILE_SIZE,
                levelOffset = 1
            )
            return MercatorTiledSurfaceImage(tileFactory, levelSet)
        }
    }
}
