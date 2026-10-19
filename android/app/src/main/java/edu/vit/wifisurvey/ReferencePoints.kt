package edu.vit.wifisurvey

/**
 * The survey grid. Mirrors db/reference_points.json so the phone and the
 * database agree on labels without the phone needing a network connection.
 *
 * Grid rows are letters, columns are numbers, so a label is its own location.
 * Exactly one point is the control point, measured first in every session.
 */
data class RefPoint(val label: String, val isControl: Boolean = false)

object ReferencePoints {
    val ALL = listOf(
        RefPoint("A1"),
        RefPoint("A3"),
        RefPoint("A5"),
        RefPoint("A7"),
        RefPoint("A9"),
        RefPoint("B2"),
        RefPoint("B6"),
        RefPoint("B10"),
        RefPoint("C1"),
        RefPoint("C2"),
        RefPoint("C3"),
        RefPoint("C4"),
        RefPoint("C5", isControl = true),
        RefPoint("C6"),
        RefPoint("C7"),
        RefPoint("C8"),
        RefPoint("C9"),
        RefPoint("C10"),
        RefPoint("D2"),
        RefPoint("D5"),
        RefPoint("D8"),
        RefPoint("E1"),
        RefPoint("E3"),
        RefPoint("E6"),
        RefPoint("E8"),
        RefPoint("E10"),
        RefPoint("F5")
    )
    val CONTROL: RefPoint get() = ALL.first { it.isControl }
    fun labels(): List<String> = ALL.map { it.label + if (it.isControl) "  [CONTROL]" else "" }
}
