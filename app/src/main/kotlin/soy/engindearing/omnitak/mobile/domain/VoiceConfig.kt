package soy.engindearing.omnitak.mobile.domain

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

data class VoiceConfig(
    val host: String = "",
    val port: Int = 64738,
    val username: String = "",
    val password: String = "",
) {
    fun isValid(): Boolean = host.isNotBlank() && host.none { it.isWhitespace() || it == '/' || it == '@' } &&
        port in 1..65535 && username.isNotBlank()
}

/** Voice secrets never fall back to plaintext preferences if the Keystore fails. */
class VoiceConfigStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "voice_credentials",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load() = VoiceConfig(
        host = prefs.getString("host", "").orEmpty(),
        port = prefs.getInt("port", 64738),
        username = prefs.getString("username", "").orEmpty(),
        password = prefs.getString("password", "").orEmpty(),
    )

    fun save(config: VoiceConfig): Boolean {
        if (!config.isValid()) return false
        return prefs.edit().putString("host", config.host.trim())
            .putInt("port", config.port).putString("username", config.username.trim())
            .putString("password", config.password).commit()
    }
}

/** An operator-selected X.509 certificate is the only trust exception for a private voice server. */
class VoiceTrustStore(private val context: Context) {
    private val file get() = File(context.filesDir, "voice-trust.p12")
    val path: String? get() = file.takeIf(File::exists)?.absolutePath

    fun importFrom(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 128_000) { "Certificate file too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("Cannot read certificate")
        return importCertificate(bytes)
    }

    fun importCertificate(bytes: ByteArray): String {
        require(bytes.size in 1..128_000) { "Certificate file must be 128 KB or smaller" }
        val cert = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
        cert.checkValidity()
        val keystore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry("voice-server", cert)
        }
        val temporary = File.createTempFile("voice-trust-", ".p12", context.filesDir)
        try {
            temporary.outputStream().use { keystore.store(it, charArrayOf()) }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
        return MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02X".format(it) }
    }
}
