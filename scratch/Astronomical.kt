package com.batoulapps.adhan2.internal

import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.internal.DoubleUtil.closestAngle
import com.batoulapps.adhan2.internal.DoubleUtil.normalizeWithBound
import com.batoulapps.adhan2.internal.DoubleUtil.unwindAngle
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Astronomical equations
 */
internal object Astronomical {
  /**
   * The geometric mean longitude of the sun in degrees.
   * @param T the julian century
   * @return the geometric longitude of the sun in degrees
   */
  fun meanSolarLongitude(T: Double): Double {
    /* Equation from Astronomical Algorithms page 163 */
    val term1 = 280.4664567
    val term2 = 36000.76983 * T
    val term3: Double = 0.0003032 * T.pow(2.0)
    val L0 = term1 + term2 + term3
    return unwindAngle(L0)
  }

  /**
   * The geometric mean longitude of the moon in degrees
   * @param T the julian century
   * @return the geometric mean longitude of the moon in degrees
   */
  fun meanLunarLongitude(T: Double): Double {
    /* Equation from Astronomical Algorithms page 144 */
    val term1 = 218.3165
    val term2 = 481267.8813 * T
    val Lp = term1 + term2
    return unwindAngle(Lp)
  }

  /**
   * The apparent longitude of the Sun, referred to the true equinox of the date.
   * @param T the julian century
   * @param L0 the mean longitude
   * @return the true equinox of the date
   */
  fun apparentSolarLongitude(T: Double, L0: Double): Double {
    /* Equation from Astronomical Algorithms page 164 */
    val longitude = L0 + solarEquationOfTheCenter(T, meanSolarAnomaly(T))
    val Ω = 125.04 - 1934.136 * T
    val λ: Double = longitude - 0.00569 - 0.00478 * sin(Ω.toRadians())
    return unwindAngle(λ)
  }

  /**
   * The ascending lunar node longitude
   * @param T the julian century
   * @return the ascending lunar node longitude
   */
  fun ascendingLunarNodeLongitude(T: Double): Double {
    /* Equation from Astronomical Algorithms page 144 */
    val term1 = 125.04452
    val term2 = 1934.136261 * T
    val term3: Double = 0.0020708 * T.pow(2.0)
    val term4: Double = T.pow(3.0) / 450000
    val Ω = term1 - term2 + term3 + term4
    return unwindAngle(Ω)
  }

  /**
   * The mean anomaly of the sun
   * @param T the julian century
   * @return the mean solar anomaly
   */
  fun meanSolarAnomaly(T: Double): Double {
    /* Equation from Astronomical Algorithms page 163 */
    val term1 = 357.52911
    val term2 = 35999.05029 * T
    val term3: Double = 0.0001537 * T.pow(2.0)
    val M = term1 + term2 - term3
    return unwindAngle(M)
  }

  /**
   * The Sun's equation of the center in degrees.
   * @param T the julian century
   * @param M the mean anomaly
   * @return the sun's equation of the center in degrees
   */
  fun solarEquationOfTheCenter(T: Double, M: Double): Double {
    /* Equation from Astronomical Algorithms page 164 */
    val Mrad: Double = M.toRadians()
    val term1: Double =
      (1.914602 - 0.004817 * T - 0.000014 * T.pow(2.0)) * sin(Mrad)
    val term2: Double = (0.019993 - 0.000101 * T) * sin(2 * Mrad)
    val term3: Double = 0.000289 * sin(3 * Mrad)
    return term1 + term2 + term3
  }

  /**
   * The mean obliquity of the ecliptic in degrees
   * formula adopted by the International Astronomical Union.
   * @param T the julian century
   * @return the mean obliquity of the ecliptic in degrees
   */
  fun meanObliquityOfTheEcliptic(T: Double): Double {
    /* Equation from Astronomical Algorithms page 147 */
    val term1 = 23.439291
    val term2 = 0.013004167 * T
    val term3: Double = 0.0000001639 * T.pow(2.0)
    val term4: Double = 0.0000005036 * T.pow(3.0)
    return term1 - term2 - term3 + term4
  }

  /**
   * The mean obliquity of the ecliptic, corrected for calculating the
   * apparent position of the sun, in degrees.
   * @param T the julian century
   * @param ε0 the mean obliquity of the ecliptic
   * @return the corrected mean obliquity of the ecliptic in degrees
   */
  fun apparentObliquityOfTheEcliptic(T: Double, ε0: Double): Double {
    /* Equation from Astronomical Algorithms page 165 */
    val O = 125.04 - 1934.136 * T
    return ε0 + 0.00256 * cos(O.toRadians())
  }

  /**
   * Mean sidereal time, the hour angle of the vernal equinox, in degrees.
   * @param T the julian century
   * @return the mean sidereal time in degrees
   */
  fun meanSiderealTime(T: Double): Double {
    /* Equation from Astronomical Algorithms page 165 */
    val JD = T * 36525 + 2451545.0
    val term1 = 280.46061837
    val term2 = 360.98564736629 * (JD - 2451545)
    val term3: Double = 0.000387933 * T.pow(2.0)
    val term4: Double = T.pow(3.0) / 38710000
    val θ = term1 + term2 + term3 - term4
    return unwindAngle(θ)
  }

  /**
   * Get the nutation in longitude
   * @param T the julian century
   * @param L0 the solar longitude
   * @param Lp the lunar longitude
   * @param Ω the ascending node
   * @return the nutation in longitude
   */
  fun nutationInLongitude(T: Double, L0: Double, Lp: Double, Ω: Double): Double {
    /* Equation from Astronomical Algorithms page 144 */
    val term1: Double = -17.2 / 3600 * sin(Ω.toRadians())
    val term2: Double = 1.32 / 3600 * sin(2 * L0.toRadians())
    val term3: Double = 0.23 / 3600 * sin(2 * Lp.toRadians())
    val term4: Double = 0.21 / 3600 * sin(2 * Ω.toRadians())
    return term1 - term2 - term3 + term4
  }

  /**
   * Get the nutation in obliquity
   * @param T the julian century
   * @param L0 the solar longitude
   * @param Lp the lunar longitude
   * @param Ω the ascending node
   * @return the nutation in obliquity
   */
  fun nutationInObliquity(T: Double, L0: Double, Lp: Double, Ω: Double): Double {
    /* Equation from Astronomical Algorithms page 144 */
    val term1: Double = 9.2 / 3600 * cos(Ω.toRadians())
    val term2: Double = 0.57 / 3600 * cos(2 * L0.toRadians())
    val term3: Double = 0.10 / 3600 * cos(2 * Lp.toRadians())
    val term4: Double = 0.09 / 3600 * cos(2 * Ω.toRadians())
    return term1 + term2 + term3 - term4
  }

  /**
   * Return the altitude of the celestial body
   * @param φ the observer latitude
   * @param δ the declination
   * @param H the local hour angle
   * @return the altitude of the celestial body
   */
  fun altitudeOfCelestialBody(φ: Double, δ: Double, H: Double): Double {
    /* Equation from Astronomical Algorithms page 93 */
    val term1: Double = sin(φ.toRadians()) * sin(δ.toRadians())
    val term2: Double = cos(φ.toRadians()) * cos(δ.toRadians()) * cos(H.toRadians())
    return asin(term1 + term2).toDegrees()
  }

  /**
   * Return the approximate transit
   * @param L the longitude
   * @param Θ0 the sidereal time
   * @param α2 the right ascension
   * @return the approximate transit
   */
  fun approximateTransit(L: Double, Θ0: Double, α2: Double): Double {
    /* Equation from page Astronomical Algorithms 102 */
    val Lw = L * -1
    val m0 = normalizeWithBound((α2 + Lw - Θ0) / 360, 1.0)
    // For locations near the International Date Line, normalizeWithBound can produce
    // an m0 for the wrong calendar date.  We detect this by comparing m0 to a
    // generalized transit time based on the longitude If they differ by more than
    // half a day, m0 is off by one cycle and we adjust in the correct direction.
    val expectedTransit = normalizeWithBound((12.0 - L / 15.0) / 24.0, 1.0)
    return when {
      m0 - expectedTransit > 0.5 -> m0 - 1.0
      expectedTransit - m0 > 0.5 -> m0 + 1.0
      else -> m0
    }
  }

  /**
   * The time at which the sun is at its highest point in the sky (in universal time)
   * @param m0 approximate transit
   * @param L the longitude
   * @param Θ0 the sidereal time
   * @param α2 the right ascension
   * @param α1 the previous right ascension
   * @param α3 the next right ascension
   * @return the time (in universal time) when the sun is at its highest point in the sky
   */
  fun correctedTransit(
    m0: Double,
    L: Double,
    Θ0: Double,
    α2: Double,
    α1: Double,
    α3: Double
  ): Double {
    /* Equation from page Astronomical Algorithms 102 */
    val Lw = L * -1
    val θ = unwindAngle(Θ0 + 360.985647 * m0)
    val α = unwindAngle(
      interpolateAngles( /* value */
        α2,  /* previousValue */α1,  /* nextValue */α3,  /* factor */m0
      )
    )
    val H = closestAngle(θ - Lw - α)
    val Δm = H / -360
    return (m0 + Δm) * 24
  }

  /**
   * Get the corrected hour angle
   * @param m0 the approximate transit
   * @param h0 the angle
   * @param coordinates the coordinates
   * @param afterTransit whether it's after transit
   * @param Θ0 the sidereal time
   * @param α2 the right ascension
   * @param α1 the previous right ascension
   * @param α3 the next right ascension
   * @param δ2 the declination
   * @param δ1 the previous declination
   * @param δ3 the next declination
   * @return the corrected hour angle
   */
  fun correctedHourAngle(
    m0: Double, h0: Double, coordinates: Coordinates, afterTransit: Boolean,
    Θ0: Double, α2: Double, α1: Double, α3: Double, δ2: Double, δ1: Double, δ3: Double
  ): Double {
    /* Equation from page Astronomical Algorithms 102 */
    val Lw = coordinates.longitude * -1
    val term1 = sin(h0.toRadians()) - sin(coordinates.latitude.toRadians()) * sin(δ2.toRadians())
    val term2 = cos(coordinates.latitude.toRadians()) * cos(δ2.toRadians())
    val H0: Double = acos(term1 / term2).toDegrees()
    val m = if (afterTransit) m0 + H0 / 360 else m0 - H0 / 360
    val θ = unwindAngle(Θ0 + 360.985647 * m)
    val α = unwindAngle(
      interpolateAngles( /* value */
        α2,  /* previousValue */α1,  /* nextValue */α3,  /* factor */m
      )
    )
    val δ = interpolate( /* value */δ2,  /* previousValue */δ1,  /* nextValue */
      δ3,  /* factor */m
    )
    val H = θ - Lw - α
    val h = altitudeOfCelestialBody( /* observerLatitude */coordinates.latitude,  /* declination */
      δ,  /* localHourAngle */H
    )
    val term3 = h - h0
    val term4 = 360 * cos(δ.toRadians()) * cos(coordinates.latitude.toRadians()) * sin(H.toRadians())
    val Δm = term3 / term4
    return (m + Δm) * 24
  }

  /**
   * Interpolation of a value given equidistant
   * previous and next values and a factor
   * equal to the fraction of the interpolated
   * point's time over the time between values.
   *
   * @param y2 the value
   * @param y1 the previous value
   * @param y3 the next value
   * @param n the factor
   * @return the interpolated value
   */
  fun interpolate(y2: Double, y1: Double, y3: Double, n: Double): Double {
    /* Equation from Astronomical Algorithms page 24 */
    val a = y2 - y1
    val b = y3 - y2
    val c = b - a
    return y2 + n / 2 * (a + b + n * c)
  }

  /**
   * Interpolation of three angles, accounting for angle unwinding
   * @param y2 value
   * @param y1 previousValue
   * @param y3 nextValue
   * @param n factor
   * @return interpolated angle
   */
  fun interpolateAngles(y2: Double, y1: Double, y3: Double, n: Double): Double {
    /* Equation from Astronomical Algorithms page 24 */
    val a = unwindAngle(y2 - y1)
    val b = unwindAngle(y3 - y2)
    val c = b - a
    return y2 + n / 2 * (a + b + n * c)
  }
}