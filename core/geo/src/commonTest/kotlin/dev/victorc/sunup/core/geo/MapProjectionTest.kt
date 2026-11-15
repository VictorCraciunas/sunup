package dev.victorc.sunup.core.geo

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import dev.victorc.sunup.core.domain.learning.GeoRegion
import kotlin.test.Test

class MapProjectionTest {

    private val europe = MapProjection(GeoRegion.EUROPE, width = 1000f, height = 1600f)

    @Test
    fun screenAndGeoRoundTrip() {
        val x = europe.screenX(-9.1f)
        val y = europe.screenY(38.7f)

        assertThat(europe.lonAt(x)).isCloseTo(-9.1f, 0.001f)
        assertThat(europe.latAt(y)).isCloseTo(38.7f, 0.001f)
    }

    @Test
    fun regionFitsAndIsCentred() {
        val left = europe.screenX(GeoRegion.EUROPE.lonMin.toFloat())
        val right = europe.screenX(GeoRegion.EUROPE.lonMax.toFloat())
        val top = europe.screenY(GeoRegion.EUROPE.latMax.toFloat())
        val bottom = europe.screenY(GeoRegion.EUROPE.latMin.toFloat())

        // Europe is wider than tall in a tall view: it fills the width and sits in the middle.
        assertThat(left).isCloseTo(0f, 0.5f)
        assertThat(right).isCloseTo(1000f, 0.5f)
        assertThat((top + bottom) / 2f).isCloseTo(800f, 0.5f)
    }

    @Test
    fun pinchKeepsThePointUnderTheFingers() {
        val focusX = 300f
        val focusY = 900f
        val lon = europe.lonAt(focusX)
        val lat = europe.latAt(focusY)

        val zoomed = europe.transformed(focusX, focusY, factor = 3f, dragX = 0f, dragY = 0f)

        assertThat(zoomed.zoom).isEqualTo(3f)
        assertThat(zoomed.screenX(lon)).isCloseTo(focusX, 0.5f)
        assertThat(zoomed.screenY(lat)).isCloseTo(focusY, 0.5f)
    }

    @Test
    fun focusingZoomsInOnASmallCountryAndCentresIt() {
        // Roughly Switzerland.
        val focused = europe.focusedOn(minLon = 6f, maxLon = 10.5f, minLat = 45.8f, maxLat = 47.8f)

        assertThat(focused.zoom).isEqualTo(MapProjection.MAX_FOCUS_ZOOM)
        assertThat(focused.screenX(8.25f)).isCloseTo(500f, 0.5f)
        assertThat(focused.screenY(46.8f)).isCloseTo(800f, 0.5f)
    }

    @Test
    fun focusingOnAHugeCountryShowsTheWholeRegion() {
        val focused = europe.focusedOn(minLon = -25f, maxLon = 45f, minLat = 34f, maxLat = 71.5f)

        assertThat(focused.zoom).isEqualTo(MapProjection.MIN_ZOOM)
    }

    @Test
    fun cannotZoomOutPastTheRegionOrDragItAway() {
        val zoomedOut = europe.transformed(500f, 800f, factor = 0.2f, dragX = 0f, dragY = 0f)
        val dragged = europe.transformed(500f, 800f, factor = 2f, dragX = 5000f, dragY = 0f)

        assertThat(zoomedOut.zoom).isEqualTo(MapProjection.MIN_ZOOM)
        assertThat(zoomedOut.panX).isCloseTo(0f, 0.01f)
        assertThat(dragged.screenX(GeoRegion.EUROPE.lonMin.toFloat())).isCloseTo(0f, 0.5f)
    }
}
