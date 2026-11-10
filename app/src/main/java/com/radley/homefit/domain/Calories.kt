package com.radley.homefit.domain

/**
 * MET-based estimate: kcal = MET × body mass (kg) × hours. It's the same formula fitness
 * trackers use without a heart-rate strap, so it is a ballpark, and the UI never shows decimals.
 */
object Calories {

    /** Used until the user logs a weight; roughly the global adult average. */
    const val DEFAULT_WEIGHT_KG = 70.0

    fun kcal(met: Double, weightKg: Double, activeMs: Long): Double =
        met * weightKg * (activeMs / 3_600_000.0)
}

object BodyMetrics {

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    enum class BmiCategory(val label: String, val upperBound: Double) {
        UNDERWEIGHT("Underweight", 18.5),
        HEALTHY("Healthy weight", 25.0),
        OVERWEIGHT("Overweight", 30.0),
        OBESE("Obese", Double.MAX_VALUE),
    }

    fun category(bmi: Double): BmiCategory = BmiCategory.entries.first { bmi < it.upperBound }

    fun kgToLb(kg: Double) = kg * 2.2046226218
    fun lbToKg(lb: Double) = lb / 2.2046226218
    fun cmToInches(cm: Double) = cm / 2.54
    fun inchesToCm(inches: Double) = inches * 2.54
}
