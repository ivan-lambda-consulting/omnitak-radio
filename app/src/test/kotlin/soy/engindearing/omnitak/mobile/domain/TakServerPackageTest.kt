package soy.engindearing.omnitak.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import soy.engindearing.omnitak.mobile.data.CaTrust
import soy.engindearing.omnitak.mobile.data.CertVault
import soy.engindearing.omnitak.mobile.data.ConnectionProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.math.BigInteger
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TakServerPackageTest {
    @Test fun previews_server_without_writing_certs_or_connecting() {
        val pkg = readZip(
            "certs/server.pref" to pref("radio.test:8089:ssl", "client.p12"),
            "certs/client.p12" to byteArrayOf(1, 2, 3),
        )
        assertEquals("radio.test", pkg.server.host)
        assertEquals(8089, pkg.server.port)
        assertEquals(ConnectionProtocol.TLS.wire, pkg.server.protocol)
        assertEquals("client.p12", pkg.clientFileName)
        assertEquals("Team TAK", pkg.server.name)
    }

    @Test fun rejects_missing_cert_invalid_port_and_unknown_protocol() {
        rejects("Client certificate missing") {
            readZip("server.pref" to pref("radio.test:8089:ssl", "missing.p12"))
        }
        rejects("Client certificate must be .p12") {
            readZip("server.pref" to pref("radio.test:8089:ssl", "client.pem"),
                "client.pem" to byteArrayOf(1))
        }
        rejects("Invalid TAK port") {
            readZip("server.pref" to pref("radio.test:99999:ssl", "client.p12"),
                "client.p12" to byteArrayOf(1))
        }
        rejects("Unsupported TAK protocol") {
            readZip("server.pref" to pref("radio.test:8089:udp", "client.p12"),
                "client.p12" to byteArrayOf(1))
        }
        rejects("Invalid TAK connect string") {
            readZip("server.pref" to pref("radio.test\nother.host:8089:tcp"))
        }
    }

    @Test fun bounds_compressed_and_uncompressed_zip_data() {
        rejects("ZIP too large") {
            TakServerPackage.read(ByteArrayInputStream(ByteArray(10 * 1024 * 1024 + 1)), "too-large.zip")
        }
        rejects("ZIP expands too far") {
            readZip("ignored.bin" to ByteArray(8 * 1024 * 1024 + 1))
        }
        rejects("ZIP entry too large") {
            readZip("server.pref" to pref("radio.test:8089:ssl", "client.p12"),
                "client.p12" to ByteArray(2 * 1024 * 1024 + 1))
        }
    }

    @Test fun rejects_ambiguous_or_traversal_entries() {
        rejects("Unsafe ZIP path") { readZip("../server.pref" to pref("radio.test:8089:tcp")) }
        rejects("Invalid server.pref XML") {
            readZip("server.pref" to "<!DOCTYPE preferences [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>".toByteArray())
        }
        rejects("Duplicate ZIP entry") {
            readZip("certs/server.pref" to pref("radio.test:8089:tcp"),
                "other/server.pref" to pref("radio.test:8089:tcp"))
        }
    }

    @Test fun invalid_client_certificate_never_writes_or_adds_server() {
        val pkg = readZip("server.pref" to pref("radio.test:8089:ssl", "client.p12"),
            "client.p12" to byteArrayOf(1, 2, 3))
        val dir = Files.createTempDirectory("radio-zip-test").toFile()
        val vault = object : CertVault(dir) {}
        var added = false
        try {
            rejects("Client certificate could not be opened") {
                pkg.install(vault) { added = true; it }
            }
            assertFalse(added)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally {
            dir.delete()
        }
    }

    @Test fun imports_client_and_ca_truststore_without_overwriting_other_certificates() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val dn = X500Name("CN=Test TAK CA")
        val now = Date()
        val cert = JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(
                dn, BigInteger.ONE, now, Date(now.time + 86_400_000L), dn, pair.public,
            ).build(JcaContentSignerBuilder("SHA256WithRSA").build(pair.private)),
        )
        val client = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setKeyEntry("client", pair.private, "test-pass".toCharArray(), arrayOf(cert))
        }
        val trust = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry("ca", cert)
        }
        val clientBytes = ByteArrayOutputStream().also { client.store(it, "test-pass".toCharArray()) }.toByteArray()
        val caBytes = ByteArrayOutputStream().also { trust.store(it, "ca-pass".toCharArray()) }.toByteArray()
        val pkg = readZip(
            "server.pref" to pref("radio.test:8089:ssl", "client.p12", "ca.p12"),
            "certs/client.p12" to clientBytes,
            "certs/ca.p12" to caBytes,
        )
        val dir = Files.createTempDirectory("radio-zip-test").toFile()
        val vault = object : CertVault(dir) {}
        try {
            val saved = pkg.install(vault) { it }
            assertEquals("test-pass", saved.certificatePassword)
            assertNotNull(saved.certificateName)
            assertNotNull(saved.caCertificateName)
            assertTrue(vault.read(saved.certificateName!!)!!.contentEquals(clientBytes))
            assertEquals(1, CaTrust.decodePemChain(vault.read(saved.caCertificateName!!)!!).size)
            rejects("Server already configured") {
                pkg.install(vault) { saved }
            }
            assertEquals("duplicate must not leave another certificate", 2, dir.listFiles()?.size)

            val pemPkg = readZip(
                "server.pref" to pref("another.test:8089:ssl", "client.p12", "ca.pem"),
                "client.p12" to clientBytes,
                "ca.pem" to CaTrust.encodePemChain(listOf(cert)),
            )
            val pemServer = pemPkg.install(vault) { it }
            assertEquals(1, CaTrust.decodePemChain(vault.read(pemServer.caCertificateName!!)!!).size)
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    private fun readZip(vararg entries: Pair<String, ByteArray>): TakServerPackage =
        TakServerPackage.read(ByteArrayInputStream(zip(*entries)), "team.zip")

    private fun pref(connect: String, cert: String? = null, ca: String? = null): ByteArray = """
        <preferences>
          <entry key="description0">Team TAK</entry>
          <entry key="connectString0">$connect</entry>
          ${cert?.let { "<entry key=\"certificateLocation\">certs/$it</entry><entry key=\"clientPassword\">test-pass</entry>" }.orEmpty()}
          ${ca?.let { "<entry key=\"caLocation\">certs/$it</entry><entry key=\"caPassword\">ca-pass</entry>" }.orEmpty()}
        </preferences>
    """.trimIndent().toByteArray()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun rejects(message: String, action: () -> Unit) {
        try {
            action()
            fail("Expected: $message")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().startsWith(message))
        }
    }
}
