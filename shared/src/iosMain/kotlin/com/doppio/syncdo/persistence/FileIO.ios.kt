package com.doppio.syncdo.persistence

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual suspend fun readTextFile(path: String): String? = withContext(Dispatchers.Default) {
    NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun writeTextFile(path: String, content: String) = withContext(Dispatchers.Default) {
    val dir = path.substringBeforeLast("/")
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    Unit
}

@OptIn(ExperimentalForeignApi::class)
actual fun getStoragePath(): String {
    val paths = platform.Foundation.NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, NSUserDomainMask, true
    )
    return (paths.firstOrNull() as? String)
        ?: error("Cannot access iOS Documents directory")
}
