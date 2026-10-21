package de.komoonlight.nav

/**
 * Ein Abbiegehinweis aus Komoots `directions`.
 * [routeIndex] = Index des Routenpunkts, an dem abgebogen wird.
 * [alongM] wird nach dem Laden aus der kumulierten Routendistanz berechnet.
 */
data class Turn(
    val routeIndex: Int,
    val type: String,
    val street: String?,
    val alongM: Double = 0.0
) {
    val kind: Kind get() = Kind.of(type)
}

/**
 * Komoot-Abbiegetypen → Ansagetext + Pfeil fürs Display.
 * Die Kürzel stammen aus Komoots inoffizieller API (z. B. "TL" = turn left);
 * unbekannte Kürzel werden über L/R im Namen grob zugeordnet.
 */
enum class Kind(val say: String, val arrow: String) {
    STRAIGHT("geradeaus", "↑"),
    SLIGHT_LEFT("leicht links", "↖"),
    LEFT("links", "←"),
    SHARP_LEFT("scharf links", "↙"),
    SLIGHT_RIGHT("leicht rechts", "↗"),
    RIGHT("rechts", "→"),
    SHARP_RIGHT("scharf rechts", "↘"),
    KEEP_LEFT("links halten", "↖"),
    KEEP_RIGHT("rechts halten", "↗"),
    U_TURN("wenden", "↓"),
    ARRIVE("Ziel erreicht", "●");

    companion object {
        fun of(t: String): Kind = when (t.uppercase()) {
            "S", "C" -> STRAIGHT
            "TSL", "BL" -> SLIGHT_LEFT
            "TL", "EL" -> LEFT
            "TSHL" -> SHARP_LEFT
            "TSR", "BR" -> SLIGHT_RIGHT
            "TR", "ER" -> RIGHT
            "TSHR" -> SHARP_RIGHT
            "KL", "FL" -> KEEP_LEFT
            "KR", "FR" -> KEEP_RIGHT
            "TU", "U" -> U_TURN
            "D", "END" -> ARRIVE
            else -> when {
                t.endsWith("L", true) -> LEFT
                t.endsWith("R", true) -> RIGHT
                else -> STRAIGHT
            }
        }
    }
}
