package dev.victorc.sunup.core.geo

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.victorc.sunup.core.domain.learning.GeoRegion
import kotlin.test.Test

class GeoAtlasTest {

    // A made-up world in the same format as the real file: a big square country with an
    // enclave hole, the enclave itself, a microstate on its border, and an African country.
    private val text = """
        {"scale":100,"countries":[
          {"id":"AA","name":"Bigland","continent":"Europe","area":500000,"askable":true,"small":false,
           "label":[500,4500],"rings":[[0,4000,1000,4000,1000,5000,0,5000],[400,4400,600,4400,600,4600,400,4600]],
           "near":["BB","CC","DD"]},
          {"id":"BB","name":"Enclavia","continent":"Europe","area":30000,"askable":true,"small":false,
           "label":[500,4500],"rings":[[400,4400,600,4400,600,4600,400,4600]]},
          {"id":"CC","name":"Tiny","continent":"Europe","area":60,"askable":true,"small":true,
           "label":[1000,4800],"rings":[[995,4795,1005,4795,1005,4805]]},
          {"id":"DD","name":"Southland","continent":"Africa","area":900000,"askable":true,"small":false,
           "label":[2000,0],"rings":[[1500,-500,2500,-500,2500,500,1500,500]]},
          {"id":"FO","name":"Faroe Islands","continent":"Europe","area":1400,"askable":false,"small":true,
           "label":[-700,6200],"rings":[[-720,6180,-680,6180,-680,6220]]}
        ]}
    """.trimIndent()

    private val atlas = GeoAtlas.parse(text)

    @Test
    fun parsesScaledCoordinates() {
        val bigland = atlas.shape("AA")!!

        assertThat(bigland.minLon).isEqualTo(0f)
        assertThat(bigland.maxLat).isEqualTo(50f)
        assertThat(bigland.labelLon).isEqualTo(5f)
    }

    @Test
    fun countriesKnowTheirNeighbours() {
        assertThat(atlas.countries(GeoRegion.EUROPE).first().nearby).containsExactly("BB", "CC", "DD")
        assertThat(atlas.shape("BB")!!.nearby).isEqualTo(emptyList())
    }

    @Test
    fun holesAreNotInside() {
        val bigland = atlas.shape("AA")!!

        assertThat(bigland.contains(2f, 42f)).isTrue()
        assertThat(bigland.contains(5f, 45f)).isFalse()
        assertThat(bigland.contains(20f, 45f)).isFalse()
    }

    @Test
    fun tapsFindTheEnclaveNotTheCountryAroundIt() {
        assertThat(atlas.shapeAt(5f, 45f)?.id).isEqualTo("BB")
        assertThat(atlas.shapeAt(2f, 42f)?.id).isEqualTo("AA")
        assertThat(atlas.shapeAt(-30f, 0f)).isNull()
    }

    @Test
    fun tapsNearASmallCountryCountForIt() {
        assertThat(atlas.shapeAt(9.5f, 47.7f, radiusLon = 1f, radiusLat = 1f)?.id).isEqualTo("CC")
        assertThat(atlas.shapeAt(9.5f, 47.7f)?.id).isEqualTo("AA")
    }

    @Test
    fun regionsListAskableCountriesBiggestFirst() {
        assertThat(atlas.countries(GeoRegion.EUROPE).map { it.id }).containsExactly("AA", "BB", "CC")
        assertThat(atlas.countries(GeoRegion.WORLD).map { it.id }).containsExactly("DD", "AA", "BB", "CC")
    }
}
