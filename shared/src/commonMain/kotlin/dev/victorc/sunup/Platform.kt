package dev.victorc.sunup

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform