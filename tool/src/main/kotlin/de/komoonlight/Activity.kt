package de.komoonlight

/**
 * Art der Aktivität – bestimmt Name und Sportart beim Upload und wie empfindlich die Auto-Pause ist.
 * [key] steht im Dateinamen der Aufzeichnung, damit der Upload später weiß, was es war.
 */
enum class Activity(
    val key: String,
    val label: String,
    /** Wie die Aufzeichnung bei Komoot heißt, z. B. „Wanderung 21.10.2026, 17:45“. */
    val tourName: String,
    /** Sportart-Kennung der Komoot-API. */
    val komootSport: String,
    /** Auto-Pause: so weit darf man sich im Stand bewegen (GPS-Rauschen). Zu Fuß kleiner, weil langsamer. */
    val pauseRadiusM: Double,
) {
    BIKE("bike", "Rad", "Radtour", "touringbicycle", pauseRadiusM = 10.0),
    HIKE("hike", "Wandern", "Wanderung", "hike", pauseRadiusM = 4.0);

    companion object {
        /** Komoot-Sportart einer geplanten Tour → Aktivität. */
        fun fromKomootSport(sport: String?): Activity = when (sport?.lowercase()) {
            "hike", "mountaineering", "jogging", "running", "walking", "snowshoe" -> HIKE
            else -> BIKE
        }

        /** Aus dem Dateinamen „2026-10-21_17-45_hike.gpx“; ältere Dateien ohne Kennung waren Radtouren. */
        fun fromFileName(file: String): Activity =
            entries.firstOrNull { file.removeSuffix(".gpx").endsWith("_${it.key}") } ?: BIKE
    }
}
