package soy.engindearing.omnitak.mobile.domain

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import soy.engindearing.omnitak.mobile.data.CaTrust
import soy.engindearing.omnitak.mobile.data.CertVault
import soy.engindearing.omnitak.mobile.data.ConnectionProtocol
import soy.engindearing.omnitak.mobile.data.TAKServer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.zip.ZipInputStream

/** Parsed in memory before either certificates or server settings are changed. */
class TakServerPackage private constructor(
    val server: TAKServer,
    val clientFileName: String?,
    val caFileName: String?,
    private val clientBytes: ByteArray?,
    private val caBytes: ByteArray?,
    private val caPassword: String?,
) {
    fun install(vault: CertVault, addServer: (TAKServer) -> TAKServer): TAKServer {
        if (clientBytes != null) {
            val store = openPkcs12(clientBytes, server.certificatePassword, "Client certificate")
            require(store.aliases().toList().any { store.isKeyEntry(it) }) {
                "Client certificate contains no private key"
            }
        }
        val caPem = caBytes?.let { bytes ->
            val chain = if (caFileName!!.endsWith(".p12", ignoreCase = true)) {
                val store = openPkcs12(bytes, caPassword, "CA truststore")
                store.aliases().toList().mapNotNull { store.getCertificate(it) as? X509Certificate }
            } else {
                CaTrust.decodePemChain(bytes)
            }
            require(chain.isNotEmpty()) { "CA certificate invalid" }
            CaTrust.encodePemChain(chain)
        }

        // Unique names prevent an import from overwriting a different server's certificates.
        val prefix = "atak-${UUID.randomUUID()}-"
        val written = mutableListOf<String>()
        try {
            val clientName = clientBytes?.let { bytes ->
                val name = clientFileName!!.substringBeforeLast('.').take(96 - prefix.length - 4) + ".p12"
                vault.importBytes(prefix + name, bytes)
                    ?.also(written::add) ?: error("Could not save client certificate")
            }
            val caName = caPem?.let { bytes ->
                vault.importBytes(prefix + "ca.pem", bytes)
                    ?.also(written::add) ?: error("Could not save CA certificate")
            }
            val imported = server.copy(certificateName = clientName, caCertificateName = caName)
            val result = addServer(imported)
            // ServerManager returns its existing object for a duplicate endpoint.
            require(result === imported) { "Server already configured" }
            return result
        } catch (e: Exception) {
            written.forEach(vault::remove)
            throw e
        }
    }

    companion object {
        private const val MAX_ZIP_BYTES = 10 * 1024 * 1024
        private const val MAX_UNCOMPRESSED_BYTES = 8 * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024
        private const val MAX_PREF_BYTES = 128 * 1024

        fun read(input: InputStream, displayName: String): TakServerPackage {
            val archive = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(archive.size() + n <= MAX_ZIP_BYTES) { "ZIP too large" }
                archive.write(buffer, 0, n)
            }

            val entries = mutableMapOf<String, ByteArray>()
            var total = 0
            var count = 0
            ZipInputStream(ByteArrayInputStream(archive.toByteArray())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(++count <= 64) { "Too many ZIP entries" }
                    val path = entry.name.replace('\\', '/')
                    require(path.length <= 256 && !path.startsWith('/') &&
                        path.split('/').none { it == ".." || it == "." }) {
                        "Unsafe ZIP path"
                    }
                    if (!entry.isDirectory) {
                        val name = path.substringAfterLast('/').lowercase()
                        val keep = name == "server.pref" || name.endsWith(".p12") ||
                            name.endsWith(".pem") || name.endsWith(".crt")
                        val bytes = ByteArrayOutputStream()
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= MAX_UNCOMPRESSED_BYTES) { "ZIP expands too far" }
                            if (keep) {
                                require(bytes.size() + n <= if (name == "server.pref") MAX_PREF_BYTES else MAX_ENTRY_BYTES) {
                                    "ZIP entry too large"
                                }
                                bytes.write(buffer, 0, n)
                            }
                        }
                        if (keep) {
                            require(name !in entries) { "Duplicate ZIP entry" }
                            entries[name] = bytes.toByteArray()
                        }
                    }
                    zip.closeEntry()
                }
            }

            val xml = entries["server.pref"] ?: throw IllegalArgumentException("server.pref missing")
            val pref = parsePref(xml)
            // shortcut: bracketed IPv6 connect strings need a parser when an IPv6 ATAK package is required.
            val parts = pref["connectString0"]?.split(':') ?: emptyList()
            require(parts.size == 3 && parts[0].isNotBlank() && parts[0].length <= 253 &&
                parts[0].none { it.isWhitespace() || it.isISOControl() }) {
                "Invalid TAK connect string"
            }
            val port = parts[1].toIntOrNull()
            require(port != null && port in 1..65535) { "Invalid TAK port" }
            val tls = when (parts[2].lowercase()) {
                "ssl", "tls" -> true
                "tcp" -> false
                else -> throw IllegalArgumentException("Unsupported TAK protocol")
            }
            val clientName = pref["certificateLocation"]?.substringAfterLast('/')?.substringAfterLast('\\')
            val caName = pref["caLocation"]?.substringAfterLast('/')?.substringAfterLast('\\')
            val client = if (tls) {
                require(!clientName.isNullOrBlank()) { "Client certificate missing" }
                require(clientName.endsWith(".p12", ignoreCase = true)) { "Client certificate must be .p12" }
                entries[clientName.lowercase()] ?: throw IllegalArgumentException("Client certificate missing")
            } else null
            val ca = caName?.let {
                require(it.endsWith(".p12", true) || it.endsWith(".pem", true) || it.endsWith(".crt", true)) {
                    "Unsupported CA format"
                }
                entries[it.lowercase()] ?: throw IllegalArgumentException("CA certificate missing")
            }
            val server = TAKServer(
                name = pref["description0"].orEmpty().ifBlank { displayName.substringBeforeLast('.') }
                    .trim().take(96).ifBlank { "TAK server" },
                host = parts[0],
                port = port,
                protocol = if (tls) ConnectionProtocol.TLS.wire else ConnectionProtocol.TCP.wire,
                useTLS = tls,
                certificateName = clientName.takeIf { tls },
                certificatePassword = pref["clientPassword"] ?: if (tls) "" else null,
            )
            return TakServerPackage(server, clientName.takeIf { tls }, caName, client, ca, pref["caPassword"])
        }

        private fun parsePref(bytes: ByteArray): Map<String, String> {
            val xml = String(bytes, Charsets.UTF_8)
            require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) {
                "Invalid server.pref XML"
            }
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(StringReader(xml))
            val values = mutableMapOf<String, String>()
            var key: String? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> if (parser.name == "entry") key = parser.getAttributeValue(null, "key")
                    XmlPullParser.TEXT -> key?.let { values[it] = parser.text?.trim().orEmpty() }
                    XmlPullParser.END_TAG -> if (parser.name == "entry") key = null
                }
                parser.next()
            }
            return values
        }

        private fun openPkcs12(bytes: ByteArray, password: String?, name: String): KeyStore = try {
            KeyStore.getInstance("PKCS12").apply {
                load(ByteArrayInputStream(bytes), password.orEmpty().toCharArray())
            }
        } catch (_: Exception) {
            throw IllegalArgumentException("$name could not be opened with its password")
        }
    }
}
