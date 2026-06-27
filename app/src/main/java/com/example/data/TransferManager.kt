package com.example.data

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class TransferManager private constructor(
    private val context: Context,
    private val dao: TransferDao
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder().build()
    
    private val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
    
    // Active jobs tracking
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    
    // Track active speed and status in-memory for live UI updating
    private val _speeds = MutableStateFlow<Map<Long, String>>(emptyMap())
    val speeds = _speeds.asStateFlow()

    companion object {
        @Volatile
        private var INSTANCE: TransferManager? = null

        fun getInstance(context: Context, dao: TransferDao): TransferManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TransferManager(context.applicationContext, dao)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Start/Resume all tasks that are in PENDING or RUNNING status
     */
    fun resumeAllIncompleteTasks() {
        scope.launch {
            val incompleteTasks = dao.getTransferTasksByStatus("PENDING") + 
                                  dao.getTransferTasksByStatus("RUNNING")
            incompleteTasks.forEach { task ->
                startTransfer(task.id)
            }
        }
    }

    fun startTransfer(taskId: Long) {
        // If already running, do nothing
        if (activeJobs.containsKey(taskId)) return

        val job = scope.launch {
            val task = dao.getTransferTaskById(taskId) ?: return@launch
            Log.d("TransferManager", "Starting transfer ${task.id}: ${task.name}")
            
            // Set status to RUNNING initially
            val runningTask = task.copy(status = "RUNNING", errorMessage = null)
            dao.updateTransferTask(runningTask)

            try {
                if (task.isDownload) {
                    runDownload(runningTask)
                } else {
                    runUpload(runningTask)
                }
            } catch (e: CancellationException) {
                Log.d("TransferManager", "Transfer $taskId was cancelled/paused")
                // Status updated to PAUSED in pauseTransfer, or handled here
                val currentTask = dao.getTransferTaskById(taskId)
                if (currentTask != null && currentTask.status == "RUNNING") {
                    dao.updateTransferTask(currentTask.copy(status = "PAUSED"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("TransferManager", "Transfer $taskId failed: ${e.message}")
                val currentTask = dao.getTransferTaskById(taskId)
                if (currentTask != null) {
                    dao.updateTransferTask(currentTask.copy(status = "FAILED", errorMessage = e.message ?: "Unknown error"))
                }
            } finally {
                activeJobs.remove(taskId)
                updateSpeed(taskId, "")
            }
        }
        activeJobs[taskId] = job
    }

    /**
     * Pause a running transfer task
     */
    fun pauseTransfer(taskId: Long) {
        val job = activeJobs[taskId]
        if (job != null) {
            job.cancel()
            activeJobs.remove(taskId)
        }
        scope.launch {
            val task = dao.getTransferTaskById(taskId)
            if (task != null && task.status != "COMPLETED" && task.status != "FAILED") {
                dao.updateTransferTask(task.copy(status = "PAUSED"))
            }
            updateSpeed(taskId, "")
        }
    }

    /**
     * Delete a transfer task and clean up its associated files
     */
    fun deleteTransfer(taskId: Long) {
        pauseTransfer(taskId)
        scope.launch {
            val task = dao.getTransferTaskById(taskId) ?: return@launch
            
            // Clean up files
            try {
                val file = File(task.localPath)
                if (file.exists()) {
                    file.delete()
                }
                
                // Clean up any part files
                val parentDir = file.parentFile
                if (parentDir != null && parentDir.exists()) {
                    parentDir.listFiles()?.forEach { child ->
                        if (child.name.startsWith(file.name + ".part")) {
                            child.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            dao.deleteTransferTask(task)
        }
    }

    /**
     * Queue a new download task
     */
    fun queueDownload(url: String, filename: String, isMultipart: Boolean = true, numParts: Int = 4, userAgent: String? = null): Long {
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val localFile = File(storageDir, filename)
        
        // Find a unique filename if it already exists
        var finalFile = localFile
        var counter = 1
        val nameWithoutExt = filename.substringBeforeLast(".")
        val ext = filename.substringAfterLast(".", "")
        val dot = if (ext.isNotEmpty()) "." else ""
        while (finalFile.exists()) {
            finalFile = File(storageDir, "$nameWithoutExt($counter)$dot$ext")
            counter++
        }

        val task = TransferTask(
            name = finalFile.name,
            url = url,
            isDownload = true,
            totalBytes = -1L, // Determined upon starting
            transferredBytes = 0L,
            status = "PENDING",
            localPath = finalFile.absolutePath,
            isMultipart = isMultipart,
            numParts = numParts,
            userAgent = userAgent
        )

        var id = 0L
        runBlocking {
            id = dao.insertTransferTask(task)
        }
        
        startTransfer(id)
        return id
    }

    /**
     * Queue a new upload task
     */
    fun queueUpload(url: String, localFile: File, isMultipart: Boolean = true, numParts: Int = 4, userAgent: String? = null): Long {
        val task = TransferTask(
            name = localFile.name,
            url = url,
            isDownload = false,
            totalBytes = localFile.length(),
            transferredBytes = 0L,
            status = "PENDING",
            localPath = localFile.absolutePath,
            isMultipart = isMultipart,
            numParts = numParts,
            userAgent = userAgent
        )

        var id = 0L
        runBlocking {
            id = dao.insertTransferTask(task)
        }
        
        startTransfer(id)
        return id
    }

    private fun updateSpeed(taskId: Long, speed: String) {
        val current = _speeds.value.toMutableMap()
        if (speed.isEmpty()) {
            current.remove(taskId)
        } else {
            current[taskId] = speed
        }
        _speeds.value = current
    }

    // ---------------- DOWNLOAD IMPLEMENTATION ----------------

    private suspend fun runDownload(task: TransferTask) = coroutineScope {
        val ua = task.userAgent ?: DEFAULT_UA
        // 1. Send an initial request to discover content length and range capabilities
        val checkRequest = Request.Builder()
            .url(task.url)
            .header("User-Agent", ua)
            .head()
            .build()
        var totalBytes = task.totalBytes
        var supportsRanges = false

        try {
            client.newCall(checkRequest).execute().use { response ->
                if (response.isSuccessful) {
                    totalBytes = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    val acceptRanges = response.header("Accept-Ranges")
                    supportsRanges = acceptRanges != null && acceptRanges.contains("bytes")
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        if (totalBytes <= 0L) {
            // Try GET with headers if HEAD failed or didn't yield size
            val getCheck = Request.Builder()
                .url(task.url)
                .header("User-Agent", ua)
                .build()
            try {
                client.newCall(getCheck).execute().use { response ->
                    if (response.isSuccessful) {
                        totalBytes = response.body?.contentLength() ?: -1L
                        val acceptRanges = response.header("Accept-Ranges")
                        supportsRanges = acceptRanges != null && acceptRanges.contains("bytes")
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        // If totalBytes is still invalid, we cannot do multipart, fall back to single-stream
        val canDoMultipart = supportsRanges && totalBytes > 0L && task.isMultipart

        // Update total bytes in DB - using a fresh copy to avoid overwriting status
        val updatedTask = task.copy(totalBytes = totalBytes, status = "RUNNING")
        dao.updateTransferTask(updatedTask)

        if (canDoMultipart) {
            runMultipartDownload(updatedTask, totalBytes)
        } else {
            runSinglePartDownload(updatedTask)
        }
    }

    private suspend fun runMultipartDownload(task: TransferTask, totalBytes: Long) = coroutineScope {
        val numParts = task.numParts
        val chunkSize = totalBytes / numParts
        val destFile = File(task.localPath)
        
        val partFiles = Array(numParts) { i -> File(destFile.absolutePath + ".part$i") }
        val partRanges = Array(numParts) { i ->
            val start = i * chunkSize
            val end = if (i == numParts - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
            start..end
        }

        // Create tasks/deferreds for each part
        val currentSpeedBytes = java.util.concurrent.atomic.AtomicLong(0)
        
        // Track last DB update to avoid flooding Room
        var lastDbUpdateTime = System.currentTimeMillis()

        // Launched speed calculator
        val speedJob = launch {
            while (isActive) {
                delay(1000)
                val bytesThisSec = currentSpeedBytes.getAndSet(0)
                val speedText = formatSpeed(bytesThisSec)
                updateSpeed(task.id, speedText)
            }
        }

        try {
            val jobs = List(numParts) { i ->
                launch(Dispatchers.IO) {
                    val partFile = partFiles[i]
                    val range = partRanges[i]
                    
                    // Resume support: check how much of this part is already downloaded
                    val existingLength = if (partFile.exists()) partFile.length() else 0L
                    
                    // If part is already completely downloaded, skip
                    val partTotalSize = (range.last - range.first) + 1
                    if (existingLength >= partTotalSize) {
                        return@launch
                    }

                    val requestStart = range.first + existingLength
                    val requestEnd = range.last
                    val ua = task.userAgent ?: DEFAULT_UA

                    val request = Request.Builder()
                        .url(task.url)
                        .header("User-Agent", ua)
                        .addHeader("Range", "bytes=$requestStart-$requestEnd")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.code != 206 && response.code != 200) {
                            throw Exception("Server returned HTTP code ${response.code} for part $i")
                        }

                        val body = response.body ?: throw Exception("Empty response body for part $i")
                        
                        // Append to the part file
                        val fos = FileOutputStream(partFile, true)
                        val bis = body.byteStream()
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (bis.read(buffer).also { bytesRead = it } != -1) {
                            ensureActive() // Check for pause cancellation
                            fos.write(buffer, 0, bytesRead)
                            currentSpeedBytes.addAndGet(bytesRead.toLong())

                            // Regularly save progress to Room
                            val now = System.currentTimeMillis()
                            if (now - lastDbUpdateTime > 800) {
                                lastDbUpdateTime = now
                                val totalDownloaded = partFiles.sumOf { if (it.exists()) it.length() else 0L }
                                val activeTask = dao.getTransferTaskById(task.id) ?: break
                                if (activeTask.status == "RUNNING") {
                                    dao.updateTransferTask(activeTask.copy(transferredBytes = totalDownloaded))
                                }
                            }
                        }
                        fos.flush()
                        fos.close()
                    }
                }
            }

            // Wait for all parts to complete
            jobs.joinAll()

            // Merge part files into destination
            speedJob.cancel()
            updateSpeed(task.id, "Merging...")
            
            val fos = FileOutputStream(destFile)
            val buffer = ByteArray(16384)
            for (i in 0 until numParts) {
                val partFile = partFiles[i]
                if (partFile.exists()) {
                    val fis = FileInputStream(partFile)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                    }
                    fis.close()
                    partFile.delete() // Delete part file after merging
                }
            }
            fos.flush()
            fos.close()

            // Complete task
            val finalTask = dao.getTransferTaskById(task.id)
            if (finalTask != null) {
                dao.updateTransferTask(finalTask.copy(
                    transferredBytes = totalBytes,
                    status = "COMPLETED"
                ))
            }
        } finally {
            speedJob.cancel()
        }
    }

    private suspend fun runSinglePartDownload(task: TransferTask) = coroutineScope {
        val destFile = File(task.localPath)
        val tempFile = File(destFile.absolutePath + ".part")
        val ua = task.userAgent ?: DEFAULT_UA
        
        val existingLength = if (tempFile.exists()) tempFile.length() else 0L
        
        val requestBuilder = Request.Builder()
            .url(task.url)
            .header("User-Agent", ua)
        if (existingLength > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingLength-")
        }
        
        val request = requestBuilder.build()
        val currentSpeedBytes = java.util.concurrent.atomic.AtomicLong(0)
        var lastDbUpdateTime = System.currentTimeMillis()

        val speedJob = launch {
            while (isActive) {
                delay(1000)
                val bytesThisSec = currentSpeedBytes.getAndSet(0)
                updateSpeed(task.id, formatSpeed(bytesThisSec))
            }
        }

        try {
            client.newCall(request).execute().use { response ->
                val isSuccessfulRange = response.code == 206
                val fos = FileOutputStream(tempFile, isSuccessfulRange && existingLength > 0L)
                val initialOffset = if (isSuccessfulRange) existingLength else 0L

                val body = response.body ?: throw Exception("Empty response body")
                val totalLength = if (isSuccessfulRange) {
                    body.contentLength() + existingLength
                } else {
                    body.contentLength()
                }

                // Update total length if unknown previously
                if (task.totalBytes <= 0 && totalLength > 0) {
                    dao.updateTransferTask(dao.getTransferTaskById(task.id)?.copy(totalBytes = totalLength) ?: task)
                }

                val bis = body.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var currentDownloaded = initialOffset

                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    ensureActive()
                    fos.write(buffer, 0, bytesRead)
                    currentDownloaded += bytesRead
                    currentSpeedBytes.addAndGet(bytesRead.toLong())

                    val now = System.currentTimeMillis()
                    if (now - lastDbUpdateTime > 800) {
                        lastDbUpdateTime = now
                        val activeTask = dao.getTransferTaskById(task.id) ?: break
                        if (activeTask.status == "RUNNING") {
                            dao.updateTransferTask(activeTask.copy(transferredBytes = currentDownloaded))
                        }
                    }
                }
                fos.flush()
                fos.close()

                // Finalize download: rename temp file to dest file
                tempFile.renameTo(destFile)

                val finalTask = dao.getTransferTaskById(task.id)
                if (finalTask != null) {
                    val finalSize = destFile.length()
                    
                    // IF 0B and we expected more, it's a failure
                    if (finalSize == 0L && task.totalBytes > 0) {
                        dao.updateTransferTask(finalTask.copy(
                            status = "FAILED",
                            errorMessage = "Downloaded file is empty (0 bytes). Link may be expired or requires specific cookies/session."
                        ))
                    } else {
                        dao.updateTransferTask(finalTask.copy(
                            transferredBytes = finalSize,
                            status = "COMPLETED"
                        ))
                    }
                }
            }
        } finally {
            speedJob.cancel()
        }
    }

    // ---------------- UPLOAD IMPLEMENTATION (MULTIPART CHUNKED OR STANDARD) ----------------

    private suspend fun runUpload(task: TransferTask) = coroutineScope {
        val file = File(task.localPath)
        if (!file.exists()) {
            throw Exception("Local file not found: ${task.localPath}")
        }

        if (task.isMultipart) {
            runMultipartUpload(task, file)
        } else {
            runStandardUpload(task, file)
        }
    }

    /**
     * Standard form multipart file upload
     */
    private suspend fun runStandardUpload(task: TransferTask, file: File) {
        val requestBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        
        // Wrap requestBody to track upload progress
        val countingBody = object : RequestBody() {
            override fun contentType(): MediaType? = requestBody.contentType()
            override fun contentLength(): Long = requestBody.contentLength()

            override fun writeTo(sink: okio.BufferedSink) {
                val fis = java.io.FileInputStream(file)
                val buffer = ByteArray(8192)
                var uploaded = 0L
                var lastDbUpdate = System.currentTimeMillis()
                val out = sink.outputStream()
                
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                    uploaded += read
                    
                    val now = System.currentTimeMillis()
                    if (now - lastDbUpdate > 800) {
                        lastDbUpdate = now
                        runBlocking {
                            val activeTask = dao.getTransferTaskById(task.id)
                            if (activeTask != null && activeTask.status == "RUNNING") {
                                dao.updateTransferTask(activeTask.copy(transferredBytes = uploaded))
                            }
                        }
                    }
                }
                out.flush()
                fis.close()
            }
        }

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, countingBody)
            .build()

        val request = Request.Builder()
            .url(task.url)
            .post(multipartBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val finalTask = dao.getTransferTaskById(task.id)
                if (finalTask != null) {
                    dao.updateTransferTask(finalTask.copy(
                        transferredBytes = file.length(),
                        status = "COMPLETED"
                    ))
                }
            } else {
                throw Exception("Upload failed with HTTP code ${response.code}")
            }
        }
    }

    /**
     * Chunked multipart upload with full Pause and Resume capability!
     * Splits file into chunk bytes, and posts chunks sequentially with indices.
     */
    private suspend fun runMultipartUpload(task: TransferTask, file: File) = coroutineScope {
        val fileLength = file.length()
        val numParts = task.numParts
        val chunkSize = fileLength / numParts
        
        var uploadedBytes = task.transferredBytes
        
        // Calculate which chunk we should resume from
        var startingChunk = (uploadedBytes / (chunkSize + 1)).toInt()
        if (startingChunk >= numParts) startingChunk = numParts - 1
        
        val currentSpeedBytes = java.util.concurrent.atomic.AtomicLong(0)
        
        val speedJob = launch {
            while (isActive) {
                delay(1000)
                val bytesThisSec = currentSpeedBytes.getAndSet(0)
                updateSpeed(task.id, formatSpeed(bytesThisSec))
            }
        }

        try {
            val fis = FileInputStream(file)
            fis.skip(startingChunk * chunkSize)

            for (i in startingChunk until numParts) {
                ensureActive()

                val chunkStart = i * chunkSize
                val chunkEnd = if (i == numParts - 1) fileLength - 1 else (i + 1) * chunkSize - 1
                val chunkLen = (chunkEnd - chunkStart + 1).toInt()

                val buffer = ByteArray(chunkLen)
                var totalRead = 0
                while (totalRead < chunkLen) {
                    val read = fis.read(buffer, totalRead, chunkLen - totalRead)
                    if (read == -1) break
                    totalRead += read
                }

                // Send chunk to server
                val mediaType = "application/octet-stream".toMediaTypeOrNull()
                val requestBody = buffer.toRequestBody(mediaType, 0, totalRead)

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chunkIndex", i.toString())
                    .addFormDataPart("chunksTotal", numParts.toString())
                    .addFormDataPart("fileId", task.id.toString())
                    .addFormDataPart("file", file.name, requestBody)
                    .build()

                val request = Request.Builder()
                    .url(task.url)
                    .post(multipartBody)
                    .addHeader("Content-Range", "bytes $chunkStart-$chunkEnd/$fileLength")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception("Chunk $i upload failed with HTTP code ${response.code}")
                    }
                }

                // Update database
                uploadedBytes += totalRead
                currentSpeedBytes.addAndGet(totalRead.toLong())

                val activeTask = dao.getTransferTaskById(task.id)
                if (activeTask != null) {
                    dao.updateTransferTask(activeTask.copy(transferredBytes = uploadedBytes))
                }
            }

            fis.close()
            speedJob.cancel()

            val finalTask = dao.getTransferTaskById(task.id)
            if (finalTask != null) {
                dao.updateTransferTask(finalTask.copy(
                    transferredBytes = fileLength,
                    status = "COMPLETED"
                ))
            }
        } finally {
            speedJob.cancel()
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024f)
            else -> "$bytesPerSec B/s"
        }
    }
}
