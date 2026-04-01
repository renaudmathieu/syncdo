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

private var storagePath: String = ""

fun initStoragePath(path: String) {
    storagePath = path
}

actual fun getStoragePath(): String = storagePath
