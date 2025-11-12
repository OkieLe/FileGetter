package io.github.ole.filegetter.getter

import kotlinx.coroutines.CoroutineScope

typealias OnProgress = (Long) -> Unit
typealias OnAbort = () -> Unit
typealias OnResult = (Boolean, String) -> Unit

class JobExecutor(private val scope: CoroutineScope) {
    private val downloader by lazy { Downloader() }
    private val extractor by lazy { Extractor(scope) }
    private val verifier by lazy { Verifier(scope) }

    private val jobInProgress = mutableMapOf<String, Job>()
    private val jobListenerMap = mutableMapOf<String, Job.Listener>()
    private val jobStateMap = mutableMapOf<String, Job.State>()

    fun execute(job: Job, listener: Job.Listener) {
        jobInProgress[job.id] = job
        jobListenerMap[job.id] = listener
        moveForward(job, Job.State.WAITING, "")
    }

    private fun moveForward(job: Job, state: Job.State, message: String) {
        jobStateMap[job.id] = state
        jobListenerMap[job.id]?.onJobState(job.id, state, message)
        when (state) {
            Job.State.SUCCESS, Job.State.FAILED, Job.State.CANCELED -> {
                jobInProgress.remove(job.id)
                jobListenerMap.remove(job.id)
                jobStateMap.remove(job.id)
            }
            Job.State.WAITING -> download(job)
            Job.State.DOWNLOADED -> verify(job)
            Job.State.VERIFIED -> extract(job)
            Job.State.EXTRACTED -> {
                moveForward(job, Job.State.SUCCESS, "")
            }
            else -> {}
        }
    }

    private fun notifyProgress(job: Job, progress: Long) {
        jobListenerMap[job.id]?.onJobProgress(job.id,
            jobStateMap[job.id] ?: Job.State.WAITING, progress)
    }

    private fun download(job: Job) {
        if (jobStateMap[job.id] != Job.State.WAITING) {
            moveForward(job, Job.State.ERROR, "Illegal state for download")
            return
        }
        moveForward(job, Job.State.DOWNLOADING, "")
        val result = downloader.download(job,
            onProgress = { progress ->
                notifyProgress(job, progress)
            },
            onResult = { result, message ->
                val state = if (result) Job.State.DOWNLOADED else Job.State.FAILED
                moveForward(job, state, message)
            },
            onAbort = {
                moveForward(job, Job.State.CANCELED,
                    "Download aborted by user")
            }
        )
        if (!result) {
            moveForward(job, Job.State.ERROR, "Cannot start download")
        }
    }

    private fun verify(job: Job) {
        if (jobStateMap[job.id] != Job.State.DOWNLOADED) {
            moveForward(job, Job.State.ERROR, "Illegal state for verify")
            return
        }
        moveForward(job, Job.State.VERIFYING, "")
        val result = verifier.verify(job, job.cachedFiles[0],
            onProgress = { progress ->
                notifyProgress(job, progress)
            },
            onResult = { result, message ->
                if (result) {
                    moveForward(job, Job.State.VERIFIED, "")
                } else {
                    moveForward(job, Job.State.FAILED, message)
                }
            }
        )
        if (!result) {
            moveForward(job, Job.State.ERROR, "Cannot start verify")
        }
    }

    private fun extract(job: Job) {
        if (jobStateMap[job.id] != Job.State.VERIFIED) {
            moveForward(job, Job.State.ERROR, "Illegal state for extract")
            return
        }
        moveForward(job, Job.State.EXTRACTING, "")
        val result = extractor.extract(job, job.cachedFiles[0],
            onProgress = { progress ->
                notifyProgress(job, progress)
            },
            onResult = { result, message ->
                if (result) {
                    moveForward(job, Job.State.EXTRACTED, "")
                } else {
                    moveForward(job, Job.State.FAILED, message)
                }
            }
        )
        if (!result) {
            moveForward(job, Job.State.ERROR, "Cannot start extract")
        }
    }

    fun cancel(id: String): Boolean {
        return jobStateMap[id]?.let { state ->
            when(state) {
                Job.State.WAITING -> {
                    jobInProgress[id]?.let {
                        moveForward(it, Job.State.CANCELED, "Not started")
                    }
                    true
                }
                Job.State.DOWNLOADING -> {
                    // wait for downloader to change state
                    downloader.cancel(id)
                    true
                }
                else -> {
                    false
                }
            }
        } ?: false
    }

    fun shutdown() {
        if (jobInProgress.isNotEmpty()) {
            downloader.shutdown()
        }
        jobInProgress.clear()
        jobListenerMap.clear()
        jobStateMap.clear()
    }
}
