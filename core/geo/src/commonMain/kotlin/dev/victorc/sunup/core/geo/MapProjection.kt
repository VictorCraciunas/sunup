package dev.victorc.sunup.core.geo

import dev.victorc.sunup.core.domain.learning.GeoRegion
import kotlin.math.PI
import kotlin.math.cos

/**
 * Lon/lat ↔ screen pixels for a [region] drawn in a [width] × [height] box.
 *
 * Plain equirectangular, with longitudes squeezed by cos(middle latitude) so countries near
 * the region's centre keep their shape. Three steps:
 * - unit:   (lon × [squeeze], −lat). Paths are built once in these coordinates.
 * - fitted: the region's box scaled to fit the view and centred.
 * - screen: fitted × [zoom] + ([panX], [panY]); the user's pinch and drag.
 */
data class MapProjection(
    val region: GeoRegion,
    val width: Float,
    val height: Float,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    val squeeze: Float = cos(((region.latMin + region.latMax) / 2.0) * PI / 180.0).toFloat()

    private val boxLeft = (region.lonMin * squeeze).toFloat()
    private val boxTop = (-region.latMax).toFloat()
    private val boxWidth = ((region.lonMax - region.lonMin) * squeeze).toFloat()
    private val boxHeight = (region.latMax - region.latMin).toFloat()

    /** Pixels per unit at zoom 1. */
    val fit: Float = if (width <= 0f || height <= 0f) 1f else minOf(width / boxWidth, height / boxHeight)
    private val fitOffsetX = (width - boxWidth * fit) / 2f
    private val fitOffsetY = (height - boxHeight * fit) / 2f

    /** Unit → screen is `unit × scale + translate`, handy for a canvas transform. */
    val scale: Float get() = fit * zoom
    val translateX: Float get() = (fitOffsetX - boxLeft * fit) * zoom + panX
    val translateY: Float get() = (fitOffsetY - boxTop * fit) * zoom + panY

    fun unitX(lon: Float): Float = lon * squeeze
    fun unitY(lat: Float): Float = -lat

    fun screenX(lon: Float): Float = unitX(lon) * scale + translateX
    fun screenY(lat: Float): Float = unitY(lat) * scale + translateY

    fun lonAt(x: Float): Float = (x - translateX) / scale / squeeze
    fun latAt(y: Float): Float = -((y - translateY) / scale)

    /** Degrees of longitude / latitude covered by [pixels] on screen, for tap radii. */
    fun lonSpan(pixels: Float): Float = pixels / scale / squeeze
    fun latSpan(pixels: Float): Float = pixels / scale

    /**
     * Zoom by [factor] around the screen point ([focusX], [focusY]), then move by
     * ([dragX], [dragY]), keeping the map on screen.
     */
    fun transformed(focusX: Float, focusY: Float, factor: Float, dragX: Float, dragY: Float): MapProjection {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val ratio = newZoom / zoom
        val moved = copy(
            zoom = newZoom,
            // The point under the fingers stays under the fingers.
            panX = focusX - (focusX - panX) * ratio + dragX,
            panY = focusY - (focusY - panY) * ratio + dragY,
        )
        return moved.clamped()
    }

    /**
     * Keeps the region filling the view: if it's smaller than the view it's centred,
     * otherwise its edges can't be dragged past the view's edges.
     */
    fun clamped(): MapProjection {
        // Where the region's edges land with no pan.
        val left = fitOffsetX * zoom
        val right = (fitOffsetX + boxWidth * fit) * zoom
        val top = fitOffsetY * zoom
        val bottom = (fitOffsetY + boxHeight * fit) * zoom
        return copy(
            panX = clampAxis(panX, left, right, width),
            panY = clampAxis(panY, top, bottom, height),
        )
    }

    private fun clampAxis(pan: Float, start: Float, end: Float, size: Float): Float {
        val content = end - start
        return if (content <= size) {
            (size - content) / 2f - start
        } else {
            pan.coerceIn(size - end, -start)
        }
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 12f
    }
}
