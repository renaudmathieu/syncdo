package com.doppio.syncdo.persistence

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual suspend fun readTextFile(path: String): String? = withContext(Dispatchers.IO) {
    val file = File(path)
    if (file.exists()) file.readText() else null
}

actual suspend fun writeTextFile(path: String, content: String): Unit = withContext(Dispatchers.IO) {
    val target = File(path)
    target.parentFile?.mkdirs()
    val tmp = File("$path.tmp")
    tmp.writeText(content)
    try {
        Files.move(
            tmp.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

actual suspend fun deleteFile(path: String): Boolean = withContext(Dispatchers.IO) {
    File(path).delete()
}

private var storagePath: String = ""

fun initStoragePath(path: String) {
    storagePath = path
}

actual fun getStoragePath(): String = storagePath
