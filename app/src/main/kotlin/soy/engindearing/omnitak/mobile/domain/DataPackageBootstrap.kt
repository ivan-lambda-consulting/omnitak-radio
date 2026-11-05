package soy.engindearing.omnitak.mobile.domain

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import soy.engindearing.omnitak.mobile.data.CertVault
import java.io.File

/** Existing ADB sideload path, now sharing the bounded importer used by the file picker. */
class DataPackageBootstrap(
    private val context: Context,
    private val certVault: CertVault,
    private val serverManager: ServerManager,
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun runIfNeeded() {
        scope.launch {
            val dir = context.getExternalFilesDir("import") ?: return@launch
            val zips = dir.listFiles { file -> file.isFile && file.name.endsWith(".zip", ignoreCase = true) }
                ?: return@launch
            for (zip in zips) {
                runCatching {
                    val candidate = zip.inputStream().use { TakServerPackage.read(it, zip.name) }
                    if (serverManager.servers.value.none { it.matchesEndpoint(candidate.server) }) {
                        candidate.install(certVault, serverManager::addServer)
                    }
                }.onSuccess {
                    val renamed = File(zip.parentFile, "${zip.name}.imported")
                    if (zip.renameTo(renamed)) Log.i(TAG, "Imported TAK data package")
                    else Log.w(TAG, "TAK data package imported but could not be marked complete")
                }.onFailure { Log.w(TAG, "TAK data package import failed: ${it.javaClass.simpleName}") }
            }
        }
    }

    private companion object {
        const val TAG = "DataPackageBootstrap"
    }
}
