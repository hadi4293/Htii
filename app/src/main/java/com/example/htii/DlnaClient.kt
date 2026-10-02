package com.example.htii

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory

class DlnaClient(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun discover(): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifiManager.createMulticastLock("parto-dlna-discovery")
        multicastLock.setReferenceCounted(false)
        multicastLock.acquire()
        try {
            val locations = discoverLocations()
            locations.mapNotNull { location ->
                runCatching { readRenderer(location) }.getOrNull()
            }.distinctBy { it.id }
        } finally {
            multicastLock.release()
        }
    }

    suspend fun play(device: DlnaDevice, mediaUrl: String, title: String, mimeType: String) =
        withContext(Dispatchers.IO) {
            val metadata = """
                <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                    xmlns:dc="http://purl.org/dc/elements/1.1/"
                    xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                    <item id="0" parentID="-1" restricted="1">
                        <dc:title>${xmlEscape(title)}</dc:title>
                        <upnp:class>object.item.videoItem</upnp:class>
                        <res protocolInfo="http-get:*:${xmlEscape(mimeType)}:*">${xmlEscape(mediaUrl)}</res>
                    </item>
                </DIDL-Lite>
            """.trimIndent()
            soap(device, "SetAVTransportURI", """
                <InstanceID>0</InstanceID>
                <CurrentURI>${xmlEscape(mediaUrl)}</CurrentURI>
                <CurrentURIMetaData>${xmlEscape(metadata)}</CurrentURIMetaData>
            """.trimIndent())
            soap(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
        }

    private fun discoverLocations(): Set<String> {
        val locations = linkedSetOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 500
            socket.broadcast = true
            val group = InetAddress.getByName("239.255.255.250")
            listOf(
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "urn:schemas-upnp-org:device:MediaRenderer:2",
            ).forEach { searchTarget ->
                val message = """
                    M-SEARCH * HTTP/1.1
                    HOST: 239.255.255.250:1900
                    MAN: "ssdp:discover"
                    MX: 1
                    ST: $searchTarget

                """.trimIndent().replace("\n", "\r\n") + "\r\n\r\n"
                val payload = message.toByteArray(StandardCharsets.US_ASCII)
                socket.send(DatagramPacket(payload, payload.size, group, 1900))
            }

            val deadline = System.currentTimeMillis() + 3_500
            while (System.currentTimeMillis() < deadline) {
                val data = ByteArray(8_192)
                val packet = DatagramPacket(data, data.size)
                try {
                    socket.receive(packet)
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                }
                val response = String(packet.data, packet.offset, packet.length, StandardCharsets.US_ASCII)
                val location = LOCATION_PATTERN.find(response)?.groupValues?.get(1)?.trim()
                if (location != null) {
                    val uri = runCatching { URI(location) }.getOrNull()
                    val address = uri?.host?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
                    if (uri?.scheme == "http" && address?.isSiteLocalAddress == true) {
                        locations += location
                    }
                }
            }
        }
        return locations
    }

    private fun readRenderer(location: String): DlnaDevice? {
        val connection = URL(location).openConnection().apply {
            connectTimeout = 2_000
            readTimeout = 2_000
        }
        require(connection.contentLengthLong <= MAX_DESCRIPTION_BYTES) {
            "DLNA device description is too large"
        }
        val description = connection.getInputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var totalBytes = 0
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                totalBytes += count
                require(totalBytes <= MAX_DESCRIPTION_BYTES) {
                    "DLNA device description is too large"
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val root = secureDocument(description).documentElement
        val baseUrl = childText(root, "URLBase")?.let { URL(it) } ?: URL(location)
        val devices = root.getElementsByTagNameNS("*", "device")
        var renderer: Element? = null
        for (index in 0 until devices.length) {
            val element = devices.item(index) as? Element ?: continue
            if (childText(element, "deviceType")
                    ?.contains(":device:MediaRenderer:", ignoreCase = true) == true
            ) {
                renderer = element
                break
            }
        }
        val device = renderer ?: return null
        val services = device.getElementsByTagNameNS("*", "service")
        for (index in 0 until services.length) {
            val service = services.item(index) as? Element ?: continue
            val serviceType = childText(service, "serviceType") ?: continue
            if (!serviceType.contains(":service:AVTransport:", ignoreCase = true)) continue
            val relativeControlUrl = childText(service, "controlURL") ?: continue
            val controlUrl = URL(baseUrl, relativeControlUrl)
            require(controlUrl.host == URL(location).host) {
                "DLNA device returned a control URL on a different host"
            }
            return DlnaDevice(
                id = childText(device, "UDN") ?: location,
                name = childText(device, "friendlyName")
                    ?: applicationContext.getString(R.string.unknown_device),
                location = location,
                controlUrl = controlUrl.toExternalForm(),
                serviceType = serviceType,
            )
        }
        return null
    }

    private fun soap(device: DlnaDevice, action: String, arguments: String) {
        val url = URL(device.controlUrl)
        val address = InetAddress.getByName(url.host)
        require(address.isSiteLocalAddress) { "آدرس تلویزیون در شبکهٔ محلی معتبر نیست." }
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body><u:$action xmlns:u="${xmlEscape(device.serviceType)}">
                    $arguments
                </u:$action></s:Body>
            </s:Envelope>
        """.trimIndent()
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 5_000
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            setRequestProperty("SOAPAction", "\"${device.serviceType}#$action\"")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException("تلویزیون درخواست $action را نپذیرفت ($status): $detail")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun secureDocument(xml: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }.newDocumentBuilder().parse(InputSource(ByteArrayInputStream(xml)))

    private fun childText(parent: Element, name: String): String? {
        val children = parent.childNodes
        for (index in 0 until children.length) {
            val child: Node = children.item(index)
            if (child is Element && child.localName == name) {
                return child.textContent.trim().takeIf(String::isNotEmpty)
            }
        }
        return null
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        const val MAX_DESCRIPTION_BYTES = 1_048_576
        val LOCATION_PATTERN = Regex("(?im)^LOCATION:\\s*(.+?)\\s*$")
    }
}
