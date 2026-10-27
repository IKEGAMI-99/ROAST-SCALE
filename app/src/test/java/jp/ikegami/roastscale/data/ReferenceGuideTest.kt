package jp.ikegami.roastscale.data

import org.junit.Assert.*
import org.junit.Test

class ReferenceGuideTest {
    @Test fun standardWeightDerivedFromHeight() {
        val p = Profile(heightCm = 170, age = 35, sex = "male")
        val reference = ReferenceGuide.weight(p)
        assertTrue(reference.applicable)
        assertEquals(53.465, reference.min!!, 0.01)
        assertEquals(71.961, reference.max!!, 0.01)
        assertTrue(reference.label.contains("BMI22"))
    }

    @Test fun femaleAgeBands() {
        val p = Profile(heightCm = 160, age = 57, sex = "female")
        assertEquals(23.0, ReferenceGuide.fat(p).min!!, 0.001)
        assertEquals(34.0, ReferenceGuide.fat(p).max!!, 0.001)
        assertEquals(45.0, ReferenceGuide.water(p).min!!, 0.001)
    }

    @Test fun noFalseUniversalTargets() {
        assertFalse(ReferenceGuide.muscle().applicable)
        assertFalse(ReferenceGuide.bone().applicable)
        assertFalse(ReferenceGuide.bmr().applicable)
        assertFalse(ReferenceGuide.visceral().applicable)
        assertNull(ReferenceGuide.protein().inRange(17.5))
        assertFalse(ReferenceGuide.fat(Profile()).applicable)
    }
}
