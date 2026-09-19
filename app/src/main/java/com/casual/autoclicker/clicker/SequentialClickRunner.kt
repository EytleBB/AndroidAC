package com.casual.autoclicker.clicker

/**
 * 与 Android 无关的串行调度器，所有方法、任务和回调应在同一个线程执行。
 * dispatch 返回是否接受手势，回调参数表示手势是否完成（false 表示取消）。
 */
internal class SequentialClickRunner<T>(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val dispatch: (T, (Boolean) -> Unit) -> Boolean
) {
    companion object {
        private const val MIN_DELAY_MS = 10L
        private const val PAUSE_POLL_MS = 200L
    }

    @Volatile
    var running = false
        private set

    private var generation = 0L
    private var points = emptyList<T>()
    private var nextIndex = 0
    private var interval: (() -> Long)? = null
    private var canDispatch: (() -> Boolean)? = null
    private var pendingTask: Runnable? = null
    private var activeAttempt: Any? = null

    fun start(points: List<T>, interval: () -> Long, canDispatch: () -> Boolean) {
        if (running || points.isEmpty()) return
        this.points = points.toList()
        this.interval = interval
        this.canDispatch = canDispatch
        nextIndex = 0
        generation++
        running = true
        dispatchOnce(generation)
    }

    fun stop() {
        running = false
        generation++
        pendingTask?.let(cancel)
        pendingTask = null
        activeAttempt = null
        points = emptyList()
        interval = null
        canDispatch = null
    }

    private fun isCurrent(run: Long) = running && generation == run

    private fun dispatchOnce(run: Long) {
        if (!isCurrent(run) || activeAttempt != null || pendingTask != null) return
        if (canDispatch?.invoke() != true) {
            scheduleNext(run, PAUSE_POLL_MS)
            return
        }
        if (!isCurrent(run)) return

        val attempt = Any()
        activeAttempt = attempt
        val accepted = dispatch(points[nextIndex]) { completed ->
            finishAttempt(run, attempt, completed)
        }
        // 即使派发期间同步触发回调，也只允许同一手势结束一次。
        if (!accepted) finishAttempt(run, attempt, completed = false)
    }

    private fun finishAttempt(run: Long, attempt: Any, completed: Boolean) {
        if (!isCurrent(run) || activeAttempt !== attempt) return
        activeAttempt = null
        if (completed) nextIndex = (nextIndex + 1) % points.size
        // 取消和拒绝都按相同间隔退避，并重试当前编号，避免漏点或连续灌入手势。
        val delay = (interval?.invoke() ?: MIN_DELAY_MS).coerceAtLeast(MIN_DELAY_MS)
        scheduleNext(run, delay)
    }

    private fun scheduleNext(run: Long, delay: Long) {
        if (!isCurrent(run) || pendingTask != null) return
        val task = object : Runnable {
            override fun run() {
                if (!isCurrent(run) || pendingTask !== this) return
                pendingTask = null
                dispatchOnce(run)
            }
        }
        pendingTask = task
        schedule(task, delay)
    }
}
