package com.radley.homefit.ui.components

import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.content.Measure
import com.radley.homefit.domain.BodyMetrics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

object Format {

    /** "x12" or "00:30", with "each side" folded in as the original app does. */
    fun measure(measure: Measure, perSide: Boolean): String {
        val base = when (measure) {
            is Measure.Reps -> "x${measure.count}"
            is Measure.Timed -> clock(measure.seconds)
        }
        return if (perSide) "$base each side" else base
    }

    fun clock(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

    /** "8 min" for previews; anything under a minute still reads as "1 min". */
    fun minutes(seconds: Int): String = "${((seconds + 30) / 60).coerceAtLeast(1)} min"

    fun kcal(kcal: Double): String = "${kcal.roundToInt()} kcal"

    fun weight(kg: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "%.1f kg".format(kg)
        UnitSystem.IMPERIAL -> "%.1f lb".format(BodyMetrics.kgToLb(kg))
    }

    fun weightValue(kg: Double, units: UnitSystem): Double =
        if (units == UnitSystem.METRIC) kg else BodyMetrics.kgToLb(kg)

    fun weightUnit(units: UnitSystem) = if (units == UnitSystem.METRIC) "kg" else "lb"

    fun height(cm: Double, units: UnitSystem): String = when (units) {
        UnitSystem.METRIC -> "${cm.roundToInt()} cm"
        UnitSystem.IMPERIAL -> {
            val inches = BodyMetrics.cmToInches(cm).roundToInt()
            "${inches / 12}′ ${inches % 12}″"
        }
    }

    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val fullDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

    fun dayMonth(date: LocalDate): String = date.format(dayMonth)
    fun fullDate(date: LocalDate): String = date.format(fullDate)

    fun weekdayInitial(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    fun muscle(name: String): String = name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
}
