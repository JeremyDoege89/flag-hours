package com.jeremy.flaghours.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaborRangeTest {
    @Test fun classify() {
        assertEquals(RangeFit.IN_RANGE, LaborRange.classify(150, 120, 180))
        assertEquals(RangeFit.IN_RANGE, LaborRange.classify(120, 120, 180))
        assertEquals(RangeFit.IN_RANGE, LaborRange.classify(180, 120, 180))
        assertEquals(RangeFit.BELOW, LaborRange.classify(100, 120, 180))
        assertEquals(RangeFit.ABOVE, LaborRange.classify(200, 120, 180))
        assertEquals(RangeFit.IN_RANGE, LaborRange.classify(150, 150, null))
        assertEquals(RangeFit.IN_RANGE, LaborRange.classify(150, 180, 120)) // swapped bounds
        assertEquals(RangeFit.BELOW, LaborRange.classify(100, 180, 120))
        assertNull(LaborRange.classify(150, null, null))
    }
}
