package nl.petrichor.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
