package com.hd.emojibattery

enum class PetDataSource {
    EMOJI_REMOTE_CONFIG,
    PET_ON_SCREEN_GITHUB,
    BOTH
}

object Constants {
    // Change this value to select the pet catalog, then rebuild the app.
    val PET_DATA_SOURCE = PetDataSource.EMOJI_REMOTE_CONFIG
}
