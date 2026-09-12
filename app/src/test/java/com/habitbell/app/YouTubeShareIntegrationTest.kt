package com.habitbell.app

import com.habitbell.app.engine.BackgroundMusicManager
import org.junit.Assert.*
import org.junit.Test

/**
 * # YouTubeShareIntegrationTest
 *
 * Comprehensive test suite validating the YouTube share intent parsing engine,
 * regex extraction of 11-character video IDs across disparate URL schemes,
 * and transformation into canonical shortest URLs (`https://youtu.be/<videoId>`).
 *
 * ## Architectural Role & Relationships
 * Validates algorithmic logic in [BackgroundMusicManager.extractVideoId] and
 * [BackgroundMusicManager.toShortestYouTubeUrl] used by the system share sheet integration
 * in `MainActivity` and `HabitBellViewModel` without requiring Android UI instrumentation.
 *
 * ## Concurrency & Concurrency Model
 * Stateless, deterministic JVM execution under JUnit 4 test runner.
 */
class YouTubeShareIntegrationTest {

    companion object {
        /** Reference 11-character video ID used across standard test vectors. */
        private const val TEST_VIDEO_ID = "x6UITRjhijI"

        /** Canonical shortest URL expectation: exactly 28 characters. */
        private const val EXPECTED_SHORTEST_URL = "https://youtu.be/x6UITRjhijI"
    }

    /**
     * Verifies extraction and canonical shortest URL generation from standard web browser watch links.
     */
    @Test
    fun testStandardWatchUrlExtraction() {
        val standardUrl = "https://www.youtube.com/watch?v=$TEST_VIDEO_ID"
        val extractedId = BackgroundMusicManager.extractVideoId(standardUrl)
        val shortestUrl = BackgroundMusicManager.toShortestYouTubeUrl(standardUrl)

        assertEquals("Video ID should be extracted from standard watch URL", TEST_VIDEO_ID, extractedId)
        assertEquals("Should produce canonical shortest URL", EXPECTED_SHORTEST_URL, shortestUrl)
        assertEquals("Canonical shortest URL should be exactly 28 characters", 28, shortestUrl?.length)
    }

    /**
     * Verifies that tracking query parameters (e.g. `?si=...`, `&feature=share`, `&t=60s`) are cleanly stripped.
     */
    @Test
    fun testShareUrlWithTrackingParametersStripped() {
        val sharedUrl = "https://youtu.be/$TEST_VIDEO_ID?si=Wp3p-mU4c123&feature=share&t=120"
        val extractedId = BackgroundMusicManager.extractVideoId(sharedUrl)
        val shortestUrl = BackgroundMusicManager.toShortestYouTubeUrl(sharedUrl)

        assertEquals("Video ID should be extracted despite query params", TEST_VIDEO_ID, extractedId)
        assertEquals("Query parameters must be completely stripped", EXPECTED_SHORTEST_URL, shortestUrl)
    }

    /**
     * Verifies extraction from YouTube Shorts links.
     */
    @Test
    fun testYouTubeShortsUrlExtraction() {
        val shortsUrl = "https://youtube.com/shorts/$TEST_VIDEO_ID?feature=share"
        val extractedId = BackgroundMusicManager.extractVideoId(shortsUrl)
        val shortestUrl = BackgroundMusicManager.toShortestYouTubeUrl(shortsUrl)

        assertEquals("Video ID should be extracted from Shorts link", TEST_VIDEO_ID, extractedId)
        assertEquals("Shorts link must resolve to canonical shortest URL", EXPECTED_SHORTEST_URL, shortestUrl)
    }

    /**
     * Verifies extraction from 24/7 YouTube Live ambient streams (`/live/<id>`).
     */
    @Test
    fun testYouTubeLiveStreamUrlExtraction() {
        val liveUrl = "https://www.youtube.com/live/$TEST_VIDEO_ID?si=ambientStream123"
        val extractedId = BackgroundMusicManager.extractVideoId(liveUrl)
        val shortestUrl = BackgroundMusicManager.toShortestYouTubeUrl(liveUrl)

        assertEquals("Video ID should be extracted from live stream link", TEST_VIDEO_ID, extractedId)
        assertEquals("Live stream link must resolve to canonical shortest URL", EXPECTED_SHORTEST_URL, shortestUrl)
    }

    /**
     * Verifies extraction from mobile web and YouTube Music links.
     */
    @Test
    fun testMobileAndMusicDomainsExtraction() {
        val mobileUrl = "https://m.youtube.com/watch?v=$TEST_VIDEO_ID"
        val musicUrl = "https://music.youtube.com/watch?v=$TEST_VIDEO_ID"

        assertEquals(EXPECTED_SHORTEST_URL, BackgroundMusicManager.toShortestYouTubeUrl(mobileUrl))
        assertEquals(EXPECTED_SHORTEST_URL, BackgroundMusicManager.toShortestYouTubeUrl(musicUrl))
    }

    /**
     * Verifies extraction when the YouTube app shares multi-line text containing video title,
     * description, and trailing shortened URL.
     */
    @Test
    fun testSharedTextWithTitleAndDescription() {
        val sharedText = "Deep Relaxing Ambient Meditation Bell Music 432Hz\nCheck out this video: https://youtu.be/$TEST_VIDEO_ID?si=trackerABC on YouTube"
        val extractedId = BackgroundMusicManager.extractVideoId(sharedText)
        val shortestUrl = BackgroundMusicManager.toShortestYouTubeUrl(sharedText)

        assertEquals("Video ID should be extracted from mixed text message", TEST_VIDEO_ID, extractedId)
        assertEquals("Mixed text message must extract canonical shortest URL", EXPECTED_SHORTEST_URL, shortestUrl)
    }

    /**
     * Verifies raw 11-character video ID input strings are recognized and converted into shortest URLs.
     */
    @Test
    fun testRawVideoIdConversion() {
        val shortestUrl = BackgroundMusicManager.toShortestYouTubeUrl(TEST_VIDEO_ID)
        assertEquals("Raw 11-char ID should convert to shortest URL", EXPECTED_SHORTEST_URL, shortestUrl)
    }

    /**
     * Verifies URL-encoded attribution or redirect links are properly decoded and resolved.
     */
    @Test
    fun testUrlEncodedRedirectLinkExtraction() {
        val encodedUrl = "https://www.youtube.com/attribution_link?a=xyz&u=%2Fwatch%3Fv%3D$TEST_VIDEO_ID%26feature%3Dshare"
        val shortestUrl = BackgroundMusicManager.toShortestYouTubeUrl(encodedUrl)
        assertEquals("Encoded redirect link must decode and resolve", EXPECTED_SHORTEST_URL, shortestUrl)
    }

    /**
     * Verifies invalid strings, random sentences, and non-YouTube links gracefully return null.
     */
    @Test
    fun testInvalidInputsReturnNull() {
        assertNull("Empty string must return null", BackgroundMusicManager.toShortestYouTubeUrl(""))
        assertNull("Whitespace string must return null", BackgroundMusicManager.toShortestYouTubeUrl("   \n\t  "))
        assertNull("Arbitrary text must return null", BackgroundMusicManager.toShortestYouTubeUrl("Just relaxing and meditating"))
        assertNull("Non-YouTube audio URL must return null", BackgroundMusicManager.toShortestYouTubeUrl("https://example.com/audio.mp3"))
        assertNull("Truncated ID must return null", BackgroundMusicManager.toShortestYouTubeUrl("https://youtu.be/tooShort"))
    }
}
