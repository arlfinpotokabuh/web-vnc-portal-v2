package com.example.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TransferManager
import com.example.data.TransferTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class TransferViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.transferDao()
    private val manager = TransferManager.getInstance(application, dao)

    // Flow of all tasks in the database
    val tasks: StateFlow<List<TransferTask>> = dao.getAllTransferTasks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Flow of current speeds
    val speeds: StateFlow<Map<Long, String>> = manager.speeds

    // Reactive list of files in the download directory
    private val _downloadedFiles = MutableStateFlow<List<File>>(emptyList())
    val downloadedFiles: StateFlow<List<File>> = _downloadedFiles.asStateFlow()

    init {
        refreshFilesList()
        manager.resumeAllIncompleteTasks()
    }

    /**
     * Refresh the list of downloaded files in internal storage
     */
    fun refreshFilesList() {
        viewModelScope.launch {
            val dir = getStorageDir()
            if (dir.exists()) {
                val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
                _downloadedFiles.value = files
            } else {
                _downloadedFiles.value = emptyList()
            }
        }
    }

    /**
     * Helper to get the downloads storage directory
     */
    fun getStorageDir(): File {
        return getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: getApplication<Application>().filesDir
    }

    /**
     * Delete a local file from the internal downloads folder
     */
    fun deleteLocalFile(file: File) {
        viewModelScope.launch {
            if (file.exists()) {
                file.delete()
                refreshFilesList()
            }
        }
    }

    /**
     * Add a download task
     */
    fun addDownload(url: String, filename: String, isMultipart: Boolean = true, numParts: Int = 4, userAgent: String? = null) {
        viewModelScope.launch {
            manager.queueDownload(url, filename, isMultipart, numParts, userAgent)
            refreshFilesList()
        }
    }

    /**
     * Add an upload task using a file from internal storage
     */
    fun addUpload(url: String, file: File, isMultipart: Boolean = true, numParts: Int = 4, userAgent: String? = null) {
        viewModelScope.launch {
            manager.queueUpload(url, file, isMultipart, numParts, userAgent)
            refreshFilesList()
        }
    }

    /**
     * Pause a running transfer task
     */
    fun pauseTransfer(taskId: Long) {
        manager.pauseTransfer(taskId)
    }

    /**
     * Resume/Start a transfer task
     */
    fun resumeTransfer(taskId: Long) {
        manager.startTransfer(taskId)
    }

    /**
     * Export a completed task to public storage
     */
    fun exportToPublic(task: TransferTask, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val file = java.io.File(task.localPath)
            if (file.exists()) {
                val uri = manager.saveToPublicDownloads(file, task.mimeType)
                onComplete(uri != null)
            } else {
                onComplete(false)
            }
        }
    }

    /**
     * Export a specific File to public storage
     */
    fun exportFileToPublic(file: File, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (file.exists()) {
                val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                val uri = manager.saveToPublicDownloads(file, mimeType)
                onComplete(uri != null)
            } else {
                onComplete(false)
            }
        }
    }

    /**
     * Delete a transfer task from database and cleanup its temporary files
     */
    fun deleteTransfer(taskId: Long) {
        viewModelScope.launch {
            manager.deleteTransfer(taskId)
            // Wait slightly for cleanup and refresh
            kotlinx.coroutines.delay(500)
            refreshFilesList()
        }
    }
}
