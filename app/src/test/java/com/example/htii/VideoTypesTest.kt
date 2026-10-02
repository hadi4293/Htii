package com.example.htii

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoTypesTest {
    @Test
    fun parsesBoundedOpenEndedAndSuffixByteRanges() {
        assertEquals(ByteRange(10, 19), ByteRangeParser.parse("bytes=10-19", 100))
        assertEquals(ByteRange(90, 99), ByteRangeParser.parse("bytes=90-", 100))
        assertEquals(ByteRange(90, 99), ByteRangeParser.parse("bytes=-10", 100))
        assertEquals(ByteRange(0, 99), ByteRangeParser.parse("bytes=-200", 100))
    }

    @Test
    fun rejectsInvalidAndMultipleByteRanges() {
        assertNull(ByteRangeParser.parse("items=0-1", 100))
        assertNull(ByteRangeParser.parse("bytes=100-101", 100))
        assertNull(ByteRangeParser.parse("bytes=20-10", 100))
        assertNull(ByteRangeParser.parse("bytes=0-1,4-5", 100))
        assertNull(ByteRangeParser.parse("bytes=-0", 100))
        assertNull(ByteRangeParser.parse("bytes=0-", 0))
    }

    @Test
    fun detectsCommonStreamingFormatsEvenWhenUrlHasAQuery() {
        assertEquals(
            "video/mp4",
            VideoTypes.fromUrl("https://media.example/video.mp4?token=sample"),
        )
        assertEquals("video/x-matroska", VideoTypes.fromUrl("http://media.example/movie.mkv"))
        assertEquals("video/mp4", VideoTypes.fromUrl("http://media.example/movie.mp4?download=1"))
        assertEquals(
            "application/vnd.apple.mpegurl",
            VideoTypes.fromUrl("https://media.example/live.m3u8"),
        )
        assertEquals("application/dash+xml", VideoTypes.fromUrl("https://media.example/movie.mpd"))
    }

    @Test
    fun rejectsUnknownFormatsAndNonHttpUrls() {
        assertNull(VideoTypes.fromUrl("https://media.example/watch"))
        assertNull(VideoTypes.validWebUrl("javascript:alert(1)"))
        assertNull(VideoTypes.validWebUrl("https:///missing-host.mp4"))
    }

    @Test
    fun ssdpSearchRequestsSupportCommonDlnaTargets() {
        assertEquals(6, SsdpDiscovery.searchTargets.size)
        val request = String(
            SsdpDiscovery.searchRequest("ssdp:all"),
            Charsets.US_ASCII,
        )
        assertEquals(true, request.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertEquals(true, request.contains("ST: ssdp:all\r\n\r\n"))
    }

    @Test
    fun readsSsdpLocationHeaderWithoutCaseSensitivity() {
        assertEquals(
            "http://192.168.1.20:1400/description.xml",
            SsdpDiscovery.location(
                "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=120\r\n LOCATION : http://192.168.1.20:1400/description.xml\r\n",
            ),
        )
        assertNull(SsdpDiscovery.location("HTTP/1.1 200 OK\r\nST: ssdp:all\r\n"))
    }

    @Test
    fun normalizesBareHostsToHttps() {
        assertEquals("https://media.example", VideoTypes.validWebUrl("media.example"))
        assertEquals("http://media.example/movie.mp4", VideoTypes.validWebUrl("http://media.example/movie.mp4"))
    }
}
