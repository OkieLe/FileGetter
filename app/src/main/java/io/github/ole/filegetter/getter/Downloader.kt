package io.github.ole.filegetter.getter

import android.util.Log
import com.liulishuo.okdownload.DownloadTask
import com.liulishuo.okdownload.StatusUtil
import com.liulishuo.okdownload.core.cause.EndCause
import com.liulishuo.okdownload.core.cause.ResumeFailedCause
import com.liulishuo.okdownload.core.listener.DownloadListener1
import com.liulishuo.okdownload.core.listener.assist.Listener1Assist

class Downloader() {
    companion object {
        private const val TAG = "Downloader"
    }

    private val downloadTasks = mutableMapOf<String, DownloadTask>()

    private inner class TaskListener(
        private val id: String,
        private val onProgress: OnProgress,
        private val onResult: OnResult,
        private val onAbort: OnAbort
    ): DownloadListener1() {
        override fun taskStart(task: DownloadTask, model: Listener1Assist.Listener1Model) {
            Log.i(TAG, "taskStart[$id]: ${task.id} ${task.url}")
            onProgress(0)
        }

        override fun retry(task: DownloadTask, cause: ResumeFailedCause) {
            Log.i(TAG, "retry[$id]: ${task.file?.name}")
        }

        override fun connected(
            task: DownloadTask,
            blockCount: Int,
            currentOffset: Long,
            totalLength: Long
        ) {
            Log.i(TAG, "connected[$id]: ${task.file?.name} $currentOffset/$totalLength")
        }

        override fun progress(
            task: DownloadTask,
            currentOffset: Long,
            totalLength: Long
        ) {
            Log.i(TAG, "progress[$id]: ${task.file?.name} $currentOffset")
            onProgress(currentOffset)
        }

        override fun taskEnd(
            task: DownloadTask,
            cause: EndCause,
            realCause: Exception?,
            model: Listener1Assist.Listener1Model
        ) {
            Log.i(TAG, "taskEnd[$id]: ${task.file?.name} $cause ${realCause?.message}")
            when (cause) {
                EndCause.COMPLETED -> {
                    Log.i(TAG, "ENDED: ${task.url}")
                    onResult(true, "")
                }
                EndCause.CANCELED -> {
                    Log.i(TAG, "CANCELED ${task.url}")
                    onAbort()
                }
                else -> {
                    Log.i(TAG, "ERROR: ${task.url} ${cause.name}")
                    onResult(false, "$cause ${realCause?.message}")
                }
            }
            downloadTasks.remove(id)
        }
    }

    /**
     * Enqueue a request to download multiple files
     * @param job the download job
     * @param onProgress the progress callback
     * @param onResult the result callback
     * @param onAbort the abort callback
     * @return the result to enqueue download task
     */
    fun download(job: Job, onProgress: OnProgress, onResult: OnResult, onAbort: OnAbort): Boolean {
        if (downloadTasks.containsKey(job.id)) {
            Log.i(TAG, "Job exists: ${job.id}")
            return false
        }
        if (downloadTasks.isNotEmpty()) {
            Log.i(TAG, "Another job running: ${downloadTasks.keys}")
            return false
        }
        Log.i(TAG, "Enqueue job: ${job.id}")
        createDownloadTask(job.url, job.cacheDir).let {
            downloadTasks[job.id] = it
            job.cachedFiles.add(it.file?.name ?: "")
            it.enqueue(TaskListener(job.id, onProgress, onResult, onAbort))
        }
        return true
    }

    private fun createDownloadTask(url: String, folder: String): DownloadTask {
        val name = url.substringAfterLast('/').substringBefore('?')
        return DownloadTask.Builder(url, folder, name)
            .setMinIntervalMillisCallbackProcess(300)
            .setPassIfAlreadyCompleted(true)
            .setAutoCallbackToUIThread(true)
            .build()
    }

    fun cancel(id: String) {
        downloadTasks.remove(id)?.let {
            Log.i(TAG, "cancel[$id]: ${StatusUtil.isCompleted(it)}")
            it.cancel()
        }
    }

    fun shutdown() {
        Log.i(TAG, "Shutdown all download tasks ${downloadTasks.size}")
        downloadTasks.keys.forEach { id -> cancel(id) }
    }
}
