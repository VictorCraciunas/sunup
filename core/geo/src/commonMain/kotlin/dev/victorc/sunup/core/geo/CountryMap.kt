package dev.victorc.sunup.core.geo

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.victorc.sunup.core.designsystem.theme.SunupColors
import dev.victorc.sunup.core.designsystem.theme.SunupTheme
import dev.victorc.sunup.core.domain.learning.GeoRegion

/**
 * The interactive map: pinch to zoom, drag to move, tap a country.
 *
 * @param found countries already found: green, with their name.
 * @param wrong the last wrong tap: orange, with its name, until the next tap.
 * @param hint a country to pulse in sun yellow (after a few misses).
 * @param onTap called with the country under the finger; taps on the sea are ignored.
 * @param resetKey when this changes (e.g. the next country), the map zooms back out.
 * @param highlight a country shown in indigo, the one to name.
 * @param focus a country to start zoomed in on; null starts with the whole region.
 */
@Composable
fun CountryMap(
    atlas: GeoAtlas,
    region: GeoRegion,
    found: Set<String>,
    wrong: String?,
    hint: String?,
    description: String,
    onTap: (GeoShape) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    highlight: String? = null,
    focus: String? = null,
) {
    val colors = SunupTheme.colors
    val sizes = SunupTheme.sizes
    val labelStyle = SunupTheme.typography.label
    val textMeasurer = rememberTextMeasurer()
    val currentOnTap by rememberUpdatedState(onTap)

    // Outlines in unit coordinates, built once per region; the canvas transform does the rest.
    val paths = remember(atlas, region) {
        val unit = MapProjection(region, width = 1f, height = 1f)
        atlas.shapes.associate { it.id to it.toPath(unit) }
    }
    // The view's size, and the user's zoom/pan on top of it. A new [resetKey] (the next
    // country) or a new region starts zoomed out again.
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var userView by remember(region, resetKey) { mutableStateOf<MapProjection?>(null) }
    fun current(): MapProjection? {
        if (viewSize.width <= 0f || viewSize.height <= 0f) return null
        val zoomed = userView
        if (zoomed != null && zoomed.width == viewSize.width && zoomed.height == viewSize.height) return zoomed
        val whole = MapProjection(region, viewSize.width, viewSize.height)
        val bounds = focus?.let { atlas.shape(it) }?.mainBounds ?: return whole
        return whole.focusedOn(bounds[0], bounds[1], bounds[2], bounds[3])
    }
    val projection = current()

    val pulse = if (hint != null) {
        rememberInfiniteTransition(label = "hint").animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 700), RepeatMode.Reverse),
            label = "hintPulse",
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { size ->
                val newSize = Size(size.width.toFloat(), size.height.toFloat())
                // Keep the user's zoom when the view resizes (e.g. the feedback panel appears).
                userView = userView?.copy(width = newSize.width, height = newSize.height)?.clamped()
                viewSize = newSize
            }
            .pointerInput(region, resetKey) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    userView = current()?.transformed(centroid.x, centroid.y, zoom, pan.x, pan.y)
                }
            }
            .pointerInput(region, resetKey, atlas) {
                detectTapGestures { offset ->
                    val p = current() ?: return@detectTapGestures
                    val radius = sizes.mapTapRadius.toPx()
                    val shape = atlas.shapeAt(
                        lon = p.lonAt(offset.x),
                        lat = p.latAt(offset.y),
                        radiusLon = p.lonSpan(radius),
                        radiusLat = p.latSpan(radius),
                    )
                    if (shape != null) currentOnTap(shape)
                }
            }
            .semantics { contentDescription = description },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(colors.mapSea)
            val p = projection ?: return@Canvas

            val visible = atlas.shapes.filter { it.isVisibleIn(p, size) }
            fun fillOf(shape: GeoShape): Color = when (shape.id) {
                in found -> colors.correct
                wrong -> colors.wrong
                highlight -> colors.indigo
                hint -> lerp(colors.mapLand, colors.sun, pulse)
                else -> if (region.includes(shape.continent)) colors.mapLand else colors.mapLandMuted
            }

            withTransform({
                translate(p.translateX, p.translateY)
                scale(p.scale, p.scale, pivot = Offset.Zero)
            }) {
                val border = Stroke(width = sizes.mapBorder.toPx() / p.scale)
                visible.forEach { shape -> paths[shape.id]?.let { drawPath(it, fillOf(shape)) } }
                visible.forEach { shape -> paths[shape.id]?.let { drawPath(it, colors.mapBorder, style = border) } }
            }

            // Tiny countries get a circle so they can be seen and tapped.
            val circleRadius = sizes.mapSmallCountry.toPx()
            visible
                .filter { it.small && it.askable && region.includes(it.continent) }
                .forEach { shape ->
                    val center = Offset(p.screenX(shape.labelLon), p.screenY(shape.labelLat))
                    val state = fillOf(shape)
                    if (state != colors.mapLand) drawCircle(state.copy(alpha = 0.85f), circleRadius, center)
                    drawCircle(colors.inkMuted, circleRadius, center, style = Stroke(width = 1.5.dp.toPx()))
                }

            // Names of found countries and of the last wrong tap.
            val labelled = (found + listOfNotNull(wrong)).mapNotNull { atlas.shape(it) }
            labelled.forEach { shape ->
                drawLabel(
                    textMeasurer = textMeasurer,
                    text = shape.name,
                    at = Offset(p.screenX(shape.labelLon), p.screenY(shape.labelLat)),
                    colors = colors,
                    style = labelStyle,
                    accent = if (shape.id == wrong) colors.wrong else colors.correct,
                )
            }
        }
    }
}

private fun GeoShape.toPath(unit: MapProjection): Path = Path().apply {
    fillType = PathFillType.EvenOdd
    rings.forEach { ring ->
        if (ring.size < 6) return@forEach
        moveTo(unit.unitX(ring[0]), unit.unitY(ring[1]))
        for (i in 2 until ring.size step 2) lineTo(unit.unitX(ring[i]), unit.unitY(ring[i + 1]))
        close()
    }
}

private fun GeoShape.isVisibleIn(p: MapProjection, size: Size): Boolean {
    val margin = 2f
    val west = p.lonAt(0f) - margin
    val east = p.lonAt(size.width) + margin
    val north = p.latAt(0f) + margin
    val south = p.latAt(size.height) - margin
    return maxLon >= west && minLon <= east && maxLat >= south && minLat <= north
}

private fun DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    at: Offset,
    colors: SunupColors,
    style: TextStyle,
    accent: Color,
) {
    val layout = textMeasurer.measure(text, style.copy(color = colors.ink))
    val padX = 8.dp.toPx()
    val padY = 3.dp.toPx()
    val width = layout.size.width + padX * 2
    val height = layout.size.height + padY * 2
    // Keep the whole label on screen even when the country is at the edge.
    val left = (at.x - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    val top = (at.y - height - 6.dp.toPx()).coerceIn(0f, (size.height - height).coerceAtLeast(0f))
    val corner = CornerRadius(height / 2f)
    drawRoundRect(colors.surface, Offset(left, top), Size(width, height), corner)
    drawRoundRect(accent, Offset(left, top), Size(width, height), corner, style = Stroke(width = 1.5.dp.toPx()))
    drawText(layout, topLeft = Offset(left + padX, top + padY))
}
