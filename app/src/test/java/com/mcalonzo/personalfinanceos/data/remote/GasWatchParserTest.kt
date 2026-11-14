package com.mcalonzo.personalfinanceos.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GasWatchParserTest {

    @Test
    fun `parses Petron Commonwealth Ave Fairview id 621 premium95 price cleanly`() {
        val fixture = javaClass.classLoader!!
            .getResource("gaswatch_quezon_city_fixture.html")!!
            .readText()
        val parsed = GasWatchParser.parseStationPremium95(fixture, targetStationId = 621)

        assertThat(parsed).isNotNull()
        assertThat(parsed!!.stationId).isEqualTo(621)
        assertThat(parsed.stationName).isEqualTo("Petron Commonwealth Ave Fairview")
        assertThat(parsed.premium95Centavos).isEqualTo(7840L)
    }

    @Test
    fun `parses Next js escaped script payload cleanly without float drift`() {
        val nextJsHtml = """
            <html><body>
            <script>self.__next_f.push([1,"5:[\"$\",\"div\",null,{\"stations\":[{\"id\":620,\"name\":\"Seaoil\",\"prices\":{\"premium95\":null}},{\"id\":621,\"brand\":\"petron\",\"name\":\"Petron Commonwealth Ave Fairview\",\"prices\":{\"premium95\":78.4,\"diesel\":61.2}}]}]\n"])</script>
            </body></html>
        """.trimIndent()

        val parsed = GasWatchParser.parseStationPremium95(nextJsHtml, targetStationId = 621)
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.stationName).isEqualTo("Petron Commonwealth Ave Fairview")
        assertThat(parsed.premium95Centavos).isEqualTo(7840L)

        val reversedOrderEscaped = """
            <script>self.__next_f.push([1,"{\"prices\":{\"diesel\":61.2,\"premium95\":78.455},\"name\":\"Petron \\\"Fairview\\\" Branch\",\"id\":621}"])</script>
        """.trimIndent()
        val parsedReversed = GasWatchParser.parseStationPremium95(reversedOrderEscaped, targetStationId = 621)
        assertThat(parsedReversed).isNotNull()
        assertThat(parsedReversed!!.stationName).isEqualTo("Petron \"Fairview\" Branch")
        assertThat(parsedReversed.premium95Centavos).isEqualTo(7846L)
    }

    @Test
    fun `does not confuse station id 6210 with target station 621`() {
        val html = """
            <script>
              [{"id":6210,"name":"Other Station","prices":{"premium95":81.0}}]
            </script>
        """.trimIndent()

        assertThat(GasWatchParser.parseStationPremium95(html, targetStationId = 621)).isNull()
    }

    @Test
    fun `returns null when premium95 is null, non-positive, non-numeric, or station 621 missing without throwing`() {
        val nullFuelHtml =
            """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":null}}</script>"""
        val zeroFuelHtml =
            """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":0.0}}</script>"""
        val negativeFuelHtml =
            """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":-78.4}}</script>"""
        val stringFuelHtml =
            """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":"78.4"}}</script>"""
        val missingPricesHtml =
            """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview"}</script>"""
        val nullNameHtml =
            """<script>{"id":621,"name":null,"prices":{"premium95":78.4}}</script>"""
        val malformedHtml =
            """<script>{"id":621,"name":"Petron Commonwealth Ave Fairview","prices":{"premium95":78."""

        assertThat(GasWatchParser.parseStationPremium95(nullFuelHtml, 621)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(zeroFuelHtml, 621)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(negativeFuelHtml, 621)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(stringFuelHtml, 621)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(missingPricesHtml, 621)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(nullNameHtml, 621)).isNull()
        assertThat(GasWatchParser.parseStationPremium95(malformedHtml, 621)).isNull()
        assertThat(GasWatchParser.parseStationPremium95("<html><body>blocked</body></html>", 621)).isNull()
        assertThat(GasWatchParser.parseStationPremium95("", 621)).isNull()
    }
}
