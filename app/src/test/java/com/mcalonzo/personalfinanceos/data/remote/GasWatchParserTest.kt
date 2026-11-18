package com.mcalonzo.personalfinanceos.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GasWatchParserTest {

    @Test
    fun `parses Petron Commonwealth Ave id 874 premium95 price cleanly`() {
        val fixture = javaClass.classLoader!!
            .getResource("gaswatch_quezon_city_fixture.html")!!
            .readText()
        val parsed = GasWatchParser.parseStationPremium95(fixture, targetStationId = 874)

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.stationId).isEqualTo(874)
        assertThat(parsed.stationName).isEqualTo("Petron Commonwealth Ave")
        assertThat(parsed.premium95Centavos).isEqualTo(7840L)
    }

    @Test
    fun `parses Next js escaped script payload cleanly without float drift`() {
        val nextJsHtml = """
            <html><body>
            <script>self.__next_f.push([1,"5:[\"$\",\"div\",null,{\"stations\":[{\"id\":620,\"name\":\"Seaoil\",\"prices\":{\"premium95\":null}},{\"id\":874,\"brand\":\"petron\",\"name\":\"Petron Commonwealth Ave\",\"prices\":{\"premium95\":78.4,\"diesel\":61.2}}]}]\n"])</script>
            </body></html>
        """.trimIndent()

        val parsed = GasWatchParser.parseStationPremium95(nextJsHtml, targetStationId = 874)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.stationName).isEqualTo("Petron Commonwealth Ave")
        assertThat(parsed.premium95Centavos).isEqualTo(7840L)

        val reversedOrderEscaped = """
            <script>self.__next_f.push([1,"{\"prices\":{\"diesel\":61.2,\"premium95\":78.455},\"name\":\"Petron \\\"Commonwealth\\\" Branch\",\"id\":874}"])</script>
        """.trimIndent()
        val parsedReversed = GasWatchParser.parseStationPremium95(reversedOrderEscaped, targetStationId = 874)
        assertThat(parsedReversed).isNotNull()
        assertThat(parsedReversed!!.stationName).isEqualTo("Petron \"Commonwealth\" Branch")
        assertThat(parsedReversed.premium95Centavos).isEqualTo(7846L)
    }

    @Test
    fun `does not confuse station id 8740 with target station 874`() {
        val html = """
            <script>
              [{"id":8740,"name":"Other Station","prices":{"premium95":81.0}}]
            </script>
        """.trimIndent()

        assertThat(GasWatchParser.parseStationPremium95(html, targetStationId = 874)).isNull()
    }

    @Test
    fun `returns null when premium95 is null, non-positive, non-numeric, or station 874 missing without throwing`() {
        val nullFuelHtml =
            """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":null}}</script>"""
        val zeroFuelHtml =
            """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":0.0}}</script>"""
        val negativeFuelHtml =
            """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":-78.4}}</script>"""
        val stringFuelHtml =
            """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":"78.4"}}</script>"""
        val missingPricesHtml =
            """<script>{"id":874,"name":"Petron Commonwealth Ave"}</script>"""
        val nullNameHtml =
            """<script>{"id":874,"name":null,"prices":{"premium95":78.4}}</script>"""
        val malformedHtml =
            """<script>{"id":874,"name":"Petron Commonwealth Ave","prices":{"premium95":78."""

        assertThat(GasWatchParser.parseStationPremium95(nullFuelHtml, 874)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(zeroFuelHtml, 874)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(negativeFuelHtml, 874)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(stringFuelHtml, 874)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(missingPricesHtml, 874)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(nullNameHtml, 874)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(malformedHtml, 874)).isNull()
        assertThat(GasWatchParser.parseStationPremium95("<html><body>blocked</body></html>", 874)).isNull()
        assertThat(GasWatchParser.parseStationPremium95("", 874)).isNull()
    }

    @Test
    fun `parseStations extracts all stations with brand and available fuel types`() {
        val html = """
            <script>
              [
                {"id": 101, "brand": "shell", "name": "Shell Makati Ave", "prices": {"unleaded": 69.5, "premium95": 75.2, "diesel": 62.1}},
                {"id": 102, "brand": "petron", "name": "Petron Ayala", "prices": {"premium95": 74.8, "premium97": 80.1, "diesel": null}}
              ]
            </script>
        """.trimIndent()

        val stations = GasWatchParser.parseStations(html)
        assertThat(stations).hasSize(2)
        val petron = stations.first { it.stationId == 102 }
        assertThat(petron.brand).isEqualTo("petron")
        assertThat(petron.stationName).isEqualTo("Petron Ayala")
        assertThat(petron.pricesCentavos).containsEntry(GasFuelType.PREMIUM_95, 7480L)
        assertThat(petron.pricesCentavos).containsEntry(GasFuelType.PREMIUM_97, 8010L)
        assertThat(petron.pricesCentavos).doesNotContainKey(GasFuelType.DIESEL)

        val shellDiesel = GasWatchParser.parseStationPrice(html, targetStationId = 101, fuelType = GasFuelType.DIESEL)
        assertThat(shellDiesel).isNotNull()
        assertThat(shellDiesel!!.premium95Centavos).isEqualTo(6210L)
    }
}
