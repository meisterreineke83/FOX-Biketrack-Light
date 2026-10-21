package de.komoonlight.geo

import java.io.Writer
import java.time.Instant
import java.util.Locale

/** Minimaler GPX-Writer – reicht für den Import in Komoot, Strava & Co. */
object Gpx {

    /** [type] z. B. „cycling“ oder „hiking“. */
    fun write(w: Writer, name: String, track: List<Pt>, type: String = "cycling") {
        w.write("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
        w.write("""<gpx version="1.1" creator="komoonlight" xmlns="http://www.topografix.com/GPX/1/1">""" + "\n")
        w.write("<trk><name>${escape(name)}</name><type>${escape(type)}</type><trkseg>\n")
        for (p in track) {
            w.write("""<trkpt lat="${p.lat}" lon="${p.lon}">""")
            p.alt?.let { w.write("<ele>%.1f</ele>".format(Locale.US, it)) }
            p.time?.let { w.write("<time>${Instant.ofEpochMilli(it)}</time>") }
            w.write("</trkpt>\n")
        }
        w.write("</trkseg></trk></gpx>\n")
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
