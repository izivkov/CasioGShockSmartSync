package org.avmedia.gshockGoogleSync.ui.watchhealth

import org.avmedia.gshockapi.io.ProfileCodec

/**
 * Height and weight are shown and edited in the units the watch's own profile flags name: inches with the imperial
 * length flag, pounds with the weight flag, else cm and kg. The scale is likely, not confirmed: the screen says so.
 */
object BodyUnits {
    fun heightUnit(p: ProfileCodec.Profile): String = if (p.imperialLength) "in" else "cm"

    fun weightUnit(p: ProfileCodec.Profile): String = if (p.weightNotKg) "lb" else "kg"

    fun heightText(p: ProfileCodec.Profile): String = "${p.height} ${heightUnit(p)}"

    fun weightText(p: ProfileCodec.Profile): String = "${p.weight} ${weightUnit(p)}"

    fun heightRange(p: ProfileCodec.Profile): IntRange = ProfileCodec.heightRange(p.imperialLength)

    fun weightRange(p: ProfileCodec.Profile): IntRange = ProfileCodec.weightRange(p.weightNotKg)

    const val CHECK = "Check on the watch"
}
