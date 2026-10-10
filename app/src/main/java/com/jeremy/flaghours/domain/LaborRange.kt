package com.jeremy.flaghours.domain

enum class RangeFit { IN_RANGE, BELOW, ABOVE }

object LaborRange {
    /** Where a flag falls against a reference range. Null when there's no usable range. */
    fun classify(flaggedHundredths: Int, lowHundredths: Int?, highHundredths: Int?): RangeFit? {
        val low = lowHundredths ?: highHundredths ?: return null
        val high = highHundredths ?: lowHundredths ?: return null
        val lo = minOf(low, high)
        val hi = maxOf(low, high)
        return when {
            flaggedHundredths < lo -> RangeFit.BELOW
            flaggedHundredths > hi -> RangeFit.ABOVE
            else -> RangeFit.IN_RANGE
        }
    }
}
