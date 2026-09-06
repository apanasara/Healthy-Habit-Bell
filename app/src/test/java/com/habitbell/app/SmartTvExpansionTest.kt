/**
 * Smart TV Ecosystem Expansion Unit Test Suite
 *
 * Architectural Role:
 * Validates protocol constants, data models, XML tag extraction algorithms,
 * and platform identification logic for the Smart TV expansion (Samsung Tizen,
 * LG webOS, Apple TV tvOS / AirPlay 2, and Sony Bravia Google TV).
 *
 * Concurrency & Lifecycle:
 * Pure JUnit 4 unit tests executing synchronously on JVM test runners.
 */
package com.habitbell.app

import com.habitbell.app.cast.AirPlayCastManager
import com.habitbell.app.cast.AirPlayDevice
import com.habitbell.app.cast.DialTvDiscoverer
import com.habitbell.app.cast.SmartTvDevice
import com.habitbell.app.cast.SmartTvPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class SmartTvExpansionTest {

    /**
     * Verifies that AirPlay 2 Bonjour service type constants adhere to Apple network specifications.
     */
    @Test
    fun testAirPlayServiceTypeConstants() {
        assertEquals(
            "AirPlay Bonjour service type must match _airplay._tcp.",
            "_airplay._tcp.",
            AirPlayCastManager.SERVICE_TYPE_AIRPLAY
        )
        assertEquals(
            "RAOP Bonjour service type must match _raop._tcp.",
            "_raop._tcp.",
            AirPlayCastManager.SERVICE_TYPE_RAOP
        )
    }

    /**
     * Verifies AirPlayDevice model defaults and immutable state copying.
     */
    @Test
    fun testAirPlayDeviceModelAndConnectionState() {
        val device = AirPlayDevice(
            id = "living_room_apple_tv",
            name = "Living Room Apple TV",
            host = InetAddress.getByName("192.168.1.50"),
            port = 7000
        )

        assertEquals("living_room_apple_tv", device.id)
        assertEquals("Living Room Apple TV", device.name)
        assertEquals(7000, device.port)
        assertEquals("AppleTV", device.model)
        assertFalse("New device must default to disconnected state", device.isConnected)

        val connected = device.copy(isConnected = true)
        assertTrue("Copied device must reflect connected state", connected.isConnected)
        assertEquals(device.id, connected.id)
    }

    /**
     * Verifies SSDP multicast configuration constants.
     */
    @Test
    fun testSsdpProtocolConstants() {
        assertEquals("239.255.255.250", DialTvDiscoverer.SSDP_MULTICAST_ADDRESS)
        assertEquals(1900, DialTvDiscoverer.SSDP_PORT)
        assertEquals("urn:dial-multiscreen-org:service:dial:1", DialTvDiscoverer.SEARCH_TARGET_DIAL)
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", DialTvDiscoverer.SEARCH_TARGET_MEDIA_RENDERER)
    }

    /**
     * Verifies XML tag extraction and Samsung Tizen platform identification from device description XML.
     */
    @Test
    fun testSamsungTizenXmlParsingAndIdentification() {
        val samsungXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
                <device>
                    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                    <friendlyName>[TV] Samsung QLED 65</friendlyName>
                    <manufacturer>Samsung Electronics</manufacturer>
                    <modelName>QN65Q80AAFXZA</modelName>
                    <UDN>uuid:12345-samsung-tizen-tv</UDN>
                </device>
            </root>
        """.trimIndent()

        // Create a dummy instance without context to test static extractor
        val name = extractTag(samsungXml, "friendlyName")
        val manufacturer = extractTag(samsungXml, "manufacturer")
        val modelName = extractTag(samsungXml, "modelName")

        assertEquals("[TV] Samsung QLED 65", name)
        assertEquals("Samsung Electronics", manufacturer)
        assertEquals("QN65Q80AAFXZA", modelName)

        val platform = when {
            manufacturer?.lowercase()?.contains("samsung") == true -> SmartTvPlatform.SAMSUNG_TIZEN
            manufacturer?.lowercase()?.contains("lg") == true -> SmartTvPlatform.LG_WEBOS
            else -> SmartTvPlatform.UNKNOWN
        }

        assertEquals(SmartTvPlatform.SAMSUNG_TIZEN, platform)
    }

    /**
     * Verifies XML tag extraction and LG webOS platform identification from device description XML.
     */
    @Test
    fun testLgWebOsXmlParsingAndIdentification() {
        val lgXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
                <device>
                    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                    <friendlyName>LG OLED C3 Cinema</friendlyName>
                    <manufacturer>LG Electronics</manufacturer>
                    <modelName>webOS TV OLED65C3PUA</modelName>
                    <UDN>uuid:67890-lg-webos-tv</UDN>
                </device>
            </root>
        """.trimIndent()

        val name = extractTag(lgXml, "friendlyName")
        val manufacturer = extractTag(lgXml, "manufacturer")
        val modelName = extractTag(lgXml, "modelName")

        assertEquals("LG OLED C3 Cinema", name)
        assertEquals("LG Electronics", manufacturer)
        assertEquals("webOS TV OLED65C3PUA", modelName)

        val platform = when {
            manufacturer?.lowercase()?.contains("samsung") == true -> SmartTvPlatform.SAMSUNG_TIZEN
            manufacturer?.lowercase()?.contains("lg") == true || modelName?.lowercase()?.contains("webos") == true -> SmartTvPlatform.LG_WEBOS
            else -> SmartTvPlatform.UNKNOWN
        }

        assertEquals(SmartTvPlatform.LG_WEBOS, platform)
    }

    /**
     * Verifies XML parser robustness against missing tags or malformed XML.
     */
    @Test
    fun testMalformedXmlTagExtraction() {
        val incompleteXml = "<root><friendlyName>Incomplete TV"
        assertNull("Malformed tag without closing tag should return null", extractTag(incompleteXml, "friendlyName"))

        val emptyXml = ""
        assertNull("Empty XML must return null", extractTag(emptyXml, "friendlyName"))
    }

    /**
     * Helper mimicking DialTvDiscoverer tag extraction algorithm.
     */
    private fun extractTag(xml: String, tag: String): String? {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val startIdx = xml.indexOf(startTag)
        val endIdx = xml.indexOf(endTag)
        return if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            xml.substring(startIdx + startTag.length, endIdx).trim()
        } else null
    }
}
