package io.github.ole.filegetter.getter

data class Job(
    /**
     * Unique id for the job
     */
    val id: String,
    /**
     * the file URL to download
     */
    val url: String,
    /**
     * md5 of the file to download
     */
    val md5: String,
    /**
     * Folder to save downloaded files
     */
    val cacheDir: String,
    /**
     * Folder to save target files
     */
    val targetDir: String,
    /**
     * File list to download, to be modified by downloader
     */
    val cachedFiles: MutableList<String> = mutableListOf(),
    /**
     * Files stored in targetDir, to be modified by extractor
     */
    val targetFiles: MutableList<String> = mutableListOf(),
    /**
     * Remove cache folder after job success
     */
    val removeCacheOnSuccess: Boolean = true
) {
    enum class State(val code: Int) {
        /**
         * Error happens in logics
         */
        ERROR(-3),

        /**
         * Failed in download/verify/extract step
         */
        FAILED(-2),

        /**
         * Canceled in download
         */
        CANCELED(-1),

        /**
         * Successfully done
         */
        SUCCESS(0),

        /**
         * Download in progress
         */
        DOWNLOADING(1),

        /**
         * Downloaded
         */
        DOWNLOADED(2),

        /**
         * Verify in progress
         */
        VERIFYING(3),

        /**
         * Verified
         */
        VERIFIED(4),

        /**
         * Extract in progress
         */
        EXTRACTING(5),

        /**
         * Extracted
         */
        EXTRACTED(6),

        /**
         * Waiting for download
         */
        WAITING(7);
    }

    interface Listener {
        /**
         * Notify download progress
         * @param id job id
         * @param progress downloaded size of file
         */
        fun onJobProgress(id: String, progress: Long)

        /**
         * Notify job state changes
         * @param id job id
         * @param state job state
         * @param message error message
         */
        fun onJobState(id: String, state: State, message: String)
    }
}
