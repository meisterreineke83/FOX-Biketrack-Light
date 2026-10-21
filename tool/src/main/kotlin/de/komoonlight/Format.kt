package de.komoonlight

import de.komoonlight.geo.Pt

/** "2026-10-12T09:31:00.000+02:00" → "12.10.2026" */
fun formatDate(iso: String?): String? {
    val d = iso?.take(10)?.split("-") ?: return null
    return if (d.size == 3 && d.all { it.isNotEmpty() }) "${d[2]}.${d[1]}.${d[0]}" else null
}

/** Fahrzeit aus den Zeitstempeln des Tracks. */
fun rideMillis(track: List<Pt>): Long {
    val a = track.firstOrNull()?.time ?: return 0
    val b = track.lastOrNull()?.time ?: return 0
    return (b - a).coerceAtLeast(0)
}

/** 2_520_000 → "0:42 h" */
fun formatDuration(ms: Long): String {
    val min = ms / 60_000
    return "%d:%02d h".format(min / 60, min % 60)
}

/** "2026-10-21_17-45.gpx" → "Radtour 21.10.2026, 17:45" (Name der Fahrt bei Komoot) */
fun uploadName(fileName: String): String {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})_(\d{2})-(\d{2})""").find(fileName) ?: return "Radtour"
    val (y, mo, d, h, mi) = m.destructured
    return "Radtour $d.$mo.$y, $h:$mi"
}

/** Aufzeichnungen, die noch hochgeladen werden müssen – älteste zuerst. */
fun pendingUploads(files: List<String>, uploaded: Set<String>): List<String> =
    files.filter { it.endsWith(".gpx") && it !in uploaded }.sorted()
