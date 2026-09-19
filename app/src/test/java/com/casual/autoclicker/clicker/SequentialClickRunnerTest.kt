package com.casual.autoclicker.clicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SequentialClickRunnerTest {
    private class Fixture {
        data class Scheduled(val task: Runnable, val delay: Long)
        data class Attempt(val point: Int, val callback: (Boolean) -> Unit)

        val pending = mutableListOf<Scheduled>()
        val attempts = mutableListOf<Attempt>()
        var allowed = true
        var interval = 100L
        var accepted = true
        var synchronousResult: Boolean? = null

        val runner = SequentialClickRunner<Int>(
            schedule = { task, delay -> pending.add(Scheduled(task, delay)) },
            cancel = { task -> pending.removeAll { it.task === task } },
            dispatch = { point, callback ->
                attempts.add(Attempt(point, callback))
                synchronousResult?.let(callback)
                accepted
            }
        )

        fun start(points: List<Int> = listOf(1, 2, 3)) =
            runner.start(points, { interval }, { allowed })

        fun runNext() {
            assertEquals("Exactly one next task should be scheduled", 1, pending.size)
            pending.removeAt(0).task.run()
        }

        fun finish(completed: Boolean = true) = attempts.last().callback(completed)

        fun clicked() = attempts.map { it.point }
    }

    @Test
    fun completesEachPointInNumberOrderAndWrapsWithoutOverlappingGestures() {
        val f = Fixture()
        f.start()
        assertEquals(listOf(1), f.clicked())
        assertTrue(f.pending.isEmpty())

        repeat(3) {
            f.finish()
            assertEquals(100L, f.pending.single().delay)
            f.runNext()
            assertTrue("Wait for gesture result before scheduling again", f.pending.isEmpty())
        }
        assertEquals(listOf(1, 2, 3, 1), f.clicked())
    }

    @Test
    fun singlePointRepeatsAndLateCompletionAfterStopDoesNotResumeIt() {
        val f = Fixture()
        f.start(listOf(7))
        repeat(2) {
            f.finish()
            f.runNext()
        }
        assertEquals(listOf(7, 7, 7), f.clicked())
        val lastCallback = f.attempts.last().callback

        f.runner.stop()
        lastCallback(true)
        assertFalse(f.runner.running)
        assertTrue(f.pending.isEmpty())
        assertEquals(listOf(7, 7, 7), f.clicked())
    }

    @Test
    fun cancelledAndRejectedGesturesRetryTheSamePoint() {
        val f = Fixture()
        f.start()
        f.finish(completed = false)
        f.accepted = false
        f.runNext()
        assertEquals(100L, f.pending.single().delay)
        f.accepted = true
        f.runNext()
        f.finish()
        f.runNext()
        assertEquals(listOf(1, 1, 1, 2), f.clicked())
    }

    @Test
    fun duplicateAndLateRejectedCallbacksCannotAdvanceOrScheduleExtraClicks() {
        val f = Fixture()
        f.accepted = false
        f.start()
        val rejectedCallback = f.attempts.single().callback
        rejectedCallback(true)
        assertEquals(1, f.pending.size)

        f.accepted = true
        f.runNext()
        val currentCallback = f.attempts.last().callback
        rejectedCallback(true)
        assertTrue(f.pending.isEmpty())
        currentCallback(true)
        currentCallback(true)
        currentCallback(false)
        f.runNext()
        assertEquals(listOf(1, 1, 2), f.clicked())
    }

    @Test
    fun restartStartsAtFirstPointAndIgnoresOldGestureCallbacks() {
        val f = Fixture()
        f.start()
        f.finish()
        f.runNext()
        val oldCallback = f.attempts.last().callback
        f.runner.stop()
        f.start()
        oldCallback(true)
        oldCallback(false)
        assertTrue(f.pending.isEmpty())
        f.finish()
        f.runNext()
        assertEquals(listOf(1, 2, 1, 2), f.clicked())
    }

    @Test
    fun stopRemovesWaitingTaskAndStaleTaskCannotAffectRestart() {
        val f = Fixture()
        f.start()
        f.finish()
        val oldTask = f.pending.single().task
        f.runner.stop()
        assertFalse(f.runner.running)
        assertTrue(f.pending.isEmpty())

        f.start()
        oldTask.run()
        assertEquals(listOf(1, 1), f.clicked())
        assertTrue(f.pending.isEmpty())
    }

    @Test
    fun foregroundGatePausesAndResumesAtTheNextNumber() {
        val f = Fixture()
        f.allowed = false
        f.start()
        assertTrue(f.attempts.isEmpty())
        assertEquals(200L, f.pending.single().delay)
        f.allowed = true
        f.runNext()
        f.finish()
        f.allowed = false
        f.runNext()
        f.runNext()
        assertEquals(listOf(1), f.clicked())
        assertEquals(200L, f.pending.single().delay)
        f.allowed = true
        f.runNext()
        assertEquals(listOf(1, 2), f.clicked())
    }

    @Test
    fun intervalChangesApplyAfterTheNextResultAndHaveAMinimum() {
        val f = Fixture()
        f.start()
        f.interval = -1L
        f.finish()
        assertEquals(10L, f.pending.single().delay)
        f.runNext()
        f.interval = 750L
        f.finish()
        assertEquals(750L, f.pending.single().delay)
    }

    @Test
    fun startSnapshotsTheSequenceAndDoesNotRestartAnActiveRun() {
        val f = Fixture()
        val points = mutableListOf(1, 2)
        f.start(points)
        points.clear()
        f.start(listOf(9))
        f.finish()
        f.runNext()
        assertEquals(listOf(1, 2), f.clicked())
    }

    @Test
    fun emptySequenceDoesNotRunOrDispatch() {
        val f = Fixture()
        f.start(emptyList())
        assertFalse(f.runner.running)
        assertTrue(f.attempts.isEmpty())
        assertTrue(f.pending.isEmpty())
    }

    @Test
    fun synchronousResultAndRejectedReturnScheduleOnlyOnce() {
        val f = Fixture()
        f.synchronousResult = true
        f.accepted = false
        f.start()
        f.runNext()
        assertEquals(listOf(1, 2), f.clicked())
        assertEquals(1, f.pending.size)
    }
}
