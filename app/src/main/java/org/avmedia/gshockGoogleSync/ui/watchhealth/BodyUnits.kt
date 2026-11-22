package org.avmedia.gshockGoogleSync.ui.watchhealth

import org.avmedia.gshockapi.io.LengthUnit

/** Height and weight are stored as cm and kg; the screen shows inches and pounds when the length unit is miles. */
object BodyUnits {
    private const val CM_PER_INCH = 2.54
    private const val LB_PER_KG = 2.2046226

    fun heightText(cm: Int, unit: LengthUnit): String = if (unit == LengthUnit.MILES) "${toInches(cm)} in" else "$cm cm"

    fun weightText(kg: Int, unit: LengthUnit): String = if (unit == LengthUnit.MILES) "${toPounds(kg)} lb" else "$kg kg"

    fun toInches(cm: Int): Int = Math.round(cm / CM_PER_INCH).toInt()

    fun toCm(inches: Int): Int = Math.round(inches * CM_PER_INCH).toInt()

    fun toPounds(kg: Int): Int = Math.round(kg * LB_PER_KG).toInt()

    fun toKg(pounds: Int): Int = Math.round(pounds / LB_PER_KG).toInt()

    /** The value shown in the edit dialog, in the display unit. */
    fun heightShown(cm: Int, unit: LengthUnit): Int = if (unit == LengthUnit.MILES) toInches(cm) else cm

    fun weightShown(kg: Int, unit: LengthUnit): Int = if (unit == LengthUnit.MILES) toPounds(kg) else kg

    fun heightStored(shown: Int, unit: LengthUnit): Int = if (unit == LengthUnit.MILES) toCm(shown) else shown

    fun weightStored(shown: Int, unit: LengthUnit): Int = if (unit == LengthUnit.MILES) toKg(shown) else shown

    /** The edit range in the display unit: the stored range 100..250 cm and 20..300 kg. */
    fun heightRange(unit: LengthUnit): IntRange = if (unit == LengthUnit.MILES) toInches(100)..toInches(250) else 100..250

    fun weightRange(unit: LengthUnit): IntRange = if (unit == LengthUnit.MILES) toPounds(20)..toPounds(300) else 20..300
}
