package com.doppio.syncdo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform