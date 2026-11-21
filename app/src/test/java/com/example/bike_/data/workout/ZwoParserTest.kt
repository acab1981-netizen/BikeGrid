package com.example.bike_.data.workout

import com.example.bike_.domain.workout.StepType
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ZwoParserTest {

    @Test
    fun parsesBasicZwo() {
        val xml = """
            <workout_file>
              <name>Test</name>
              <description>Desc</description>
              <workout>
                <Warmup Duration="600" PowerLow="0.4" PowerHigh="0.7"/>
                <SteadyState Duration="300" Power="0.85" Cadence="90"/>
                <IntervalsT Repeat="2" OnDuration="60" OffDuration="60" OnPower="1.0" OffPower="0.5"/>
                <Cooldown Duration="300" PowerLow="0.6" PowerHigh="0.4"/>
              </workout>
            </workout_file>
        """.trimIndent()
        val w = ZwoParser.parse(ByteArrayInputStream(xml.toByteArray()), "test.zwo")
        assertNotNull(w)
        w!!
        assertEquals("Test", w.title)
        assertTrue(w.steps.isNotEmpty())
        assertEquals(StepType.WARMUP, w.steps.first().type)
        assertEquals(StepType.COOLDOWN, w.steps.last().type)
        // warmup 40-70%
        assertEquals(40, w.steps[0].targetMin)
        assertEquals(70, w.steps[0].targetMax)
        // steady 85% -> ~82-88
        val steady = w.steps[1]
        assertEquals(StepType.WORK, steady.type)
        assertTrue(steady.targetMin in 80..86)
        assertTrue(steady.targetMax in 84..90)
        // intervals: 2*(on+off)=4 steps
        assertEquals(7, w.steps.size)
    }
}
