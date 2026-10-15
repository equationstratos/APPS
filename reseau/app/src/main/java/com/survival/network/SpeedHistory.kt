package com.survival.network

data class SpeedResult(
    val timeMillis: Long,
    val connection: ConnectionType,
    val latencyMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double
)

/** Line-based storage format, independent of the display locale. */
object SpeedHistory {
    const val MAX_ENTRIES = 5

    fun encode(results: List<SpeedResult>): String = results.joinToString("\n") { r ->
        listOf(r.timeMillis, r.connection.name, r.latencyMs, r.downloadMbps, r.uploadMbps).joinToString(";")
    }

    fun decode(raw: String?): List<SpeedResult> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { parseLine(it) }.take(MAX_ENTRIES).toList()
    }

    private fun parseLine(line: String): SpeedResult? {
        val parts = line.split(';')
        if (parts.size != 5) return null
        val time = parts[0].toLongOrNull() ?: return null
        val type = ConnectionType.entries.firstOrNull { it.name == parts[1] } ?: return null
        val values = parts.drop(2).map { field ->
            field.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        }
        return SpeedResult(time, type, values[0], values[1], values[2])
    }
}
