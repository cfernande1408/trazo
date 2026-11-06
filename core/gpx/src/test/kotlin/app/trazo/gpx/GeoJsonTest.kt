package app.trazo.gpx

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoJsonTest {

    @Test
    fun `track como MultiLineString en orden lon lat`() {
        val json = GeoJson.track(
            listOf(
                listOf(GeoPoint(40.5, -4.25), GeoPoint(40.6, -4.2)),
                listOf(GeoPoint(41.0, -4.0)), // un solo punto: no es una línea
            )
        )
        assertEquals(
            """{"type":"Feature","properties":{},"geometry":{"type":"MultiLineString","coordinates":[[[-4.25,40.5],[-4.2,40.6]]]}}""",
            json,
        )
    }

    @Test
    fun `puntos como FeatureCollection`() {
        assertEquals(
            """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[-4.0,40.0]}}]}""",
            GeoJson.points(listOf(40.0 to -4.0)),
        )
    }
}
