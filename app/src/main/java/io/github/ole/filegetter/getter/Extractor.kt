package io.github.ole.filegetter.getter

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File

class Extractor(private val scope: CoroutineScope) {
    companion object {
        private const val TAG = "Extractor"
    }

    fun extract(job: Job, file: String, onProgress: OnProgress, onResult: OnResult): Boolean {
        val source = File(job.cacheDir, file)
        if (!source.exists()) {
            return false
        }
        val destDir = File(job.targetDir)
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        unzip(source.absolutePath, destDir, { progress ->
            onProgress(progress.toLong())
        }, { success, errMsg ->
            if (success) {
                job.targetFiles.addAll(destDir.listFiles()?.map { it.name } ?: emptyList())
                onResult(true, "")
                if (job.removeCacheOnSuccess) {
                    scope.launch(Dispatchers.IO) {
                        val result = source.delete()
                        Log.i(TAG, "Deleting cache: $source -> $result")
                    }
                }
            } else {
                onResult(false, errMsg ?: "")
            }
        })
        return true
    }

    private fun unzip(
        source: String,
        destDir: File,
        onProgress: ((Int) -> Unit)? = null,
        onResult: (success: Boolean, errMsg: String?) -> Unit
    ) {
        try {
            val zip = ZipFile(source).apply {
                isRunInThread = true
            }
            if (zip.isValidZipFile.not()) {
                onResult(false, "Invalid zip file")
                return
            }

            zip.extractAll(destDir.absolutePath)

            val monitor = zip.progressMonitor
            scope.launch {
                while (monitor.state != ProgressMonitor.State.READY) {
                    val percent = monitor.percentDone
                    Log.i(TAG, "Extract progress: $percent of ${monitor.fileName}")
                    withContext(Dispatchers.Main) {
                        onProgress?.invoke(percent)
                    }
                    delay(300L)
                }
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Unzip failed", e)
            onResult(false, e.localizedMessage)
        }
    }
}
