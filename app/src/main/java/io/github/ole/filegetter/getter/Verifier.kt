package io.github.ole.filegetter.getter

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class Verifier(private val scope: CoroutineScope) {
    companion object {
        private const val TAG = "Verifier"
        private const val DEFAULT_BUFFER_SIZE = 1024 * 16
    }

    fun verify(job: Job, file: String, onProgress: OnProgress, onResult: OnResult): Boolean {
        val source = File(job.cacheDir, file)
        if (source.exists().not()) {
            Log.i(TAG, "File $file does not exist")
            return false
        }
        scope.launch {
            source.md5Equals(job.md5, {
                onProgress(it.toLong())
            }, { success ->
                onResult(success, if (success) "" else "MD5 mismatch")
            })
        }
        return true
    }

    private fun md5Equals(expectedHex: String, actualHex: String): Boolean {
        val exp = expectedHex.lowercase().toByteArray()
        val act = actualHex.lowercase().toByteArray()
        Log.i(TAG, "Comparing expected: $expectedHex, actual: $actualHex")
        return MessageDigest.isEqual(exp, act)
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private suspend fun File.md5Equals(md5: String, onProgress: ((Int) -> Unit), onResult: ((Boolean) -> Unit)) {
        return withContext(Dispatchers.IO) {
            val total = length()
            var processed = 0L
            val md = MessageDigest.getInstance("MD5")
            BufferedInputStream(FileInputStream(this@md5Equals)).use { bis ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read: Int
                var currentPercent = 0
                while (bis.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                    processed += read
                    val percent = ((processed * 100) / total).toInt()
                    if (percent > currentPercent) {
                        currentPercent = percent
                        Log.i(TAG, "Verify progress: $currentPercent")
                        withContext(Dispatchers.Main) { onProgress(percent) }
                    }
                }
            }
            val matches = md5Equals(md5, md.digest().toHex())
            withContext(Dispatchers.Main) {
                onResult(matches)
            }
        }
    }
}
