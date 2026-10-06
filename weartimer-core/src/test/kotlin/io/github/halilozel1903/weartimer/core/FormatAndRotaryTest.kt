package io.github.halilozel1903.weartimer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FormatAndRotaryTest {
    @Test
    fun clockRoundsDown() {
        assertEquals("0:00", TimeFormat.clock(999))
        assertEquals("0:05", TimeFormat.clock(5_400))
        assertEquals("12:34", TimeFormat.clock(754_000))
        assertEquals("1:02:03", TimeFormat.clock(3_723_000))
        assertEquals("0:00", TimeFormat.clock(-10))
    }

    @Test
    fun countdownRoundsUp() {
        assertEquals("0:01", TimeFormat.countdown(400))
        assertEquals("0:20", TimeFormat.countdown(20_000))
        assertEquals("0:13", TimeFormat.countdown(12_001))
        assertEquals("0:00", TimeFormat.countdown(0))
        assertEquals("1:00:00", TimeFormat.countdown(3_599_001))
    }

    @Test
    fun compactAndSpoken() {
        assertEquals("45s", TimeFormat.compact(45_000))
        assertEquals("4m", TimeFormat.compact(240_000))
        assertEquals("4m 30s", TimeFormat.compact(270_000))
        assertEquals("1h 5m", TimeFormat.compact(3_900_000))
        assertEquals("2h", TimeFormat.compact(7_200_000))
        assertEquals("4 minutes 30 seconds", TimeFormat.spoken(270_000))
        assertEquals("1 hour 1 second", TimeFormat.spoken(3_601_000))
        assertEquals("0 seconds", TimeFormat.spoken(0))
    }

    @Test
    fun accumulatorCarriesRemainders() {
        val accumulator = RotaryStepAccumulator(stepPixels = 48f, idleResetMillis = 300)
        assertEquals(0, accumulator.accumulate(30f, 0))
        assertEquals(1, accumulator.accumulate(30f, 10))
        assertEquals(2, accumulator.accumulate(96f, 20))
    }

    @Test
    fun accumulatorDropsRemaindersOnDirectionChangeAndIdle() {
        val accumulator = RotaryStepAccumulator(stepPixels = 48f, idleResetMillis = 300)
        accumulator.accumulate(40f, 0)
        assertEquals(0, accumulator.accumulate(-40f, 10))
        assertEquals(-1, accumulator.accumulate(-10f, 20))
        accumulator.accumulate(-40f, 30)
        assertEquals(0, accumulator.accumulate(-40f, 1_000))
        assertFailsWith<IllegalArgumentException> { RotaryStepAccumulator(stepPixels = 0f) }
    }

    @Test
    fun stepperGrowsStepsWithTheValue() {
        val stepper = DurationStepper.default()
        assertEquals(60_000L, stepper.step(55_000, 1))
        assertEquals(75_000L, stepper.step(60_000, 1))
        assertEquals(55_000L, stepper.step(60_000, -1))
        assertEquals(60_000L, stepper.step(75_000, -1))
        assertEquals(10_000L, stepper.step(7_000, 1))
        assertEquals(5_000L, stepper.step(7_000, -1))
        assertEquals(90_000L, stepper.step(55_000, 3))
    }

    @Test
    fun stepperClampsAndSnaps() {
        val stepper = DurationStepper.default(minMillis = 5_000, maxMillis = 600_000)
        assertEquals(5_000L, stepper.step(10_000, -10))
        assertEquals(600_000L, stepper.step(590_000, 5))
        assertEquals(20_000L, stepper.snap(21_000))
        assertEquals(75_000L, stepper.snap(70_000))
        assertEquals(0.5f, DurationStepper.uniform(60_000, 0, 600_000).fraction(300_000))
        assertEquals(120_000L, DurationStepper.uniform(60_000, 60_000, 600_000).step(60_000, 1))
    }
}
