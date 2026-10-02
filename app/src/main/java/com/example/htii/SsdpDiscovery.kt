package com.example.htii

internal object SsdpDiscovery {
    val searchTargets = listOf(
        "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:device:MediaRenderer:2",
        "urn:schemas-upnp-org:device:MediaRenderer:3",
        "urn:schemas-upnp-org:service:AVTransport:1",
        "upnp:rootdevice",
        "ssdp:all",
    )

    fun searchRequest(target: String): ByteArray = listOf(
        "M-SEARCH * HTTP/1.1",
        "HOST: 239.255.255.250:1900",
        "MAN: \"ssdp:discover\"",
        "MX: 2",
        "ST: $target",
    ).joinToString("\r\n").plus("\r\n\r\n").toByteArray(Charsets.US_ASCII)

    fun location(response: String): String? = response.lineSequence()
        .firstOrNull { it.substringBefore(':').trim().equals("location", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf(String::isNotBlank)
}
