package com.example.htii

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.TimeUnit

data class DlnaDevice(
    val id: String,
    val name: String,
    val controlUrl: String,
    val serviceType: String,
)

class DlnaController(context: Context) {
    private val applicationContext = context.applicationContext

    fun discoverDevices(): List<DlnaDevice> {
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        val multicastLock = wifi.createMulticastLock("parto-dlna-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            val locations = linkedSetOf<String>()
            val networkInterface = wifiNetworkInterface()
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(0))
                socket.networkInterface = networkInterface
                socket.timeToLive = MULTICAST_TTL
                socket.soTimeout = DISCOVERY_READ_TIMEOUT_MS
                SsdpDiscovery.searchTargets.forEach { target ->
                    val payload = SsdpDiscovery.searchRequest(target)
                    socket.send(
                        DatagramPacket(
                            payload,
                            payload.size,
                            SSDP_ADDRESS,
                            SSDP_PORT,
                        ),
                    )
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DISCOVERY_DURATION_SECONDS)
                val buffer = ByteArray(8_192)
                while (System.nanoTime() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val response = String(packet.data, packet.offset, packet.length, Charsets.US_ASCII)
                        SsdpDiscovery.location(response)?.let(locations::add)
                    } catch (_: java.net.SocketTimeoutException) {
                        continue
                    }
                }
            }
            val devices = mutableListOf<DlnaDevice>()
            val descriptionErrors = mutableListOf<Exception>()
            locations.forEach { location ->
                try {
                    readDeviceDescription(location)?.let(devices::add)
                } catch (exception: Exception) {
                    descriptionErrors += exception
                }
            }
            if (devices.isEmpty() && descriptionErrors.isNotEmpty()) {
                throw IllegalStateException(
                    "پاسخ تلویزیون دریافت شد اما مشخصات DLNA خوانده نشد: " +
                        (descriptionErrors.first().localizedMessage ?: "خطای ناشناخته"),
                    descriptionErrors.first(),
                )
            }
            return devices.distinctBy(DlnaDevice::id)
        } finally {
            multicastLock.release()
        }
    }

    private fun wifiNetworkInterface(): NetworkInterface {
        val connectivity = applicationContext.getSystemService(ConnectivityManager::class.java)
        val activeName = connectivity.activeNetwork
            ?.let(connectivity::getLinkProperties)
            ?.interfaceName
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
        return interfaces.firstOrNull { it.name == activeName }
            ?: interfaces.firstOrNull { it.name.startsWith("wlan", ignoreCase = true) }
            ?: throw IllegalStateException("رابط شبکهٔ Wi-Fi پیدا نشد؛ اتصال گوشی را بررسی کنید.")
    }

    fun play(device: DlnaDevice, mediaUrl: String, title: String, mimeType: String) {
        val metadata = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
              xmlns:dc="http://purl.org/dc/elements/1.1/"
              xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="0" parentID="0" restricted="1">
                <dc:title>${xmlEscape(title)}</dc:title>
                <upnp:class>object.item.videoItem</upnp:class>
                <res protocolInfo="http-get:*:${xmlEscape(mimeType)}:*">${xmlEscape(mediaUrl)}</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
        invokeAction(
            device,
            "SetAVTransportURI",
            "<InstanceID>0</InstanceID><CurrentURI>${xmlEscape(mediaUrl)}</CurrentURI>" +
                "<CurrentURIMetaData>${xmlEscape(metadata)}</CurrentURIMetaData>",
        )
        invokeAction(device, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    private fun readDeviceDescription(location: String): DlnaDevice? {
        val connection = URL(location).openConnection() as? HttpURLConnection
            ?: throw IllegalStateException("دریافت مشخصات تلویزیون ممکن نشد.")
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.requestMethod = "GET"
        try {
            if (connection.responseCode !in 200..299) return null
            val parser = Xml.newPullParser()
            parser.setInput(connection.inputStream, null)
            var friendlyName = ""
            var udn = location
            var serviceType: String? = null
            var controlPath: String? = null
            var currentServiceType = ""
            var currentControlPath = ""

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "service" -> {
                            currentServiceType = ""
                            currentControlPath = ""
                        }
                        "friendlyName" -> friendlyName = parser.nextText()
                        "UDN" -> udn = parser.nextText()
                        "serviceType" -> currentServiceType = parser.nextText()
                        "controlURL" -> currentControlPath = parser.nextText()
                    }
                } else if (
                    parser.eventType == XmlPullParser.END_TAG &&
                    parser.name == "service" &&
                    currentServiceType.contains(":service:AVTransport:", ignoreCase = true) &&
                    currentControlPath.isNotBlank()
                ) {
                    serviceType = currentServiceType
                    controlPath = currentControlPath
                }
                parser.next()
            }
            val control = controlPath ?: return null
            return DlnaDevice(
                id = udn,
                name = friendlyName.ifBlank { URL(location).host },
                controlUrl = URL(URL(location), control).toString(),
                serviceType = serviceType ?: return null,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun invokeAction(device: DlnaDevice, action: String, arguments: String) {
        val connection = URL(device.controlUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = HTTP_TIMEOUT_MS
        connection.readTimeout = HTTP_TIMEOUT_MS
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPAction", "\"${device.serviceType}#$action\"")
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
              s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body><u:$action xmlns:u="${xmlEscape(device.serviceType)}">$arguments</u:$action></s:Body>
            </s:Envelope>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        try {
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IllegalStateException(
                    "تلویزیون فرمان $action را نپذیرفت (HTTP $status)" +
                        detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty(),
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        val SSDP_ADDRESS = java.net.InetAddress.getByName("239.255.255.250")
        const val SSDP_PORT = 1900
        const val DISCOVERY_READ_TIMEOUT_MS = 600
        const val DISCOVERY_DURATION_SECONDS = 4L
        const val HTTP_TIMEOUT_MS = 5_000
        const val MULTICAST_TTL = 2
    }
}
