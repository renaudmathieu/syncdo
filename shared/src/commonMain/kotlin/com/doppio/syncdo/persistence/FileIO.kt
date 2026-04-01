package com.doppio.syncdo.persistence

expect suspend fun readTextFile(path: String): String?
expect suspend fun writeTextFile(path: String, content: String)
expect fun getStoragePath(): String
