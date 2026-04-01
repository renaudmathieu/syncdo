package com.doppio.syncdo.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun readTextFile(path: String): String? = withContext(Dispatchers.IO) {
    val file = File(path)
    if (file.exists()) file.readText() else null
}

actual suspend fun writeTextFile(path: String, content: String) = withContext(Dispatchers.IO) {
    val file = File(path)
    file.parentFile?.mkdirs()
    file.writeText(content)
}

actual fun getStoragePath(): String {
    // Allow override via system property for multi-instance testing
    System.getProperty("syncDo.storagePath")?.let { return it }
    val home = System.getProperty("user.home")
    return "$home/.syncdo"
}
