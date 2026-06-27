package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HistoryEntity
import com.example.data.ShortcutEntity
import com.example.data.ShortcutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShortcutViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ShortcutRepository
    val shortcuts: StateFlow<List<ShortcutEntity>>
    val history: StateFlow<List<HistoryEntity>>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ShortcutRepository(database.shortcutDao())
        shortcuts = repository.allShortcuts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        history = repository.recentHistory.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // Active multitasking sessions
    private val _activeSessions = MutableStateFlow<List<ShortcutEntity>>(emptyList())
    val activeSessions: StateFlow<List<ShortcutEntity>> = _activeSessions.asStateFlow()

    private val _focusedSessionIndex = MutableStateFlow(-1)
    val focusedSessionIndex: StateFlow<Int> = _focusedSessionIndex.asStateFlow()

    val activeShortcut: StateFlow<ShortcutEntity?> = _activeSessions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        .let { sessionsFlow ->
            _focusedSessionIndex.let { indexFlow ->
                MutableStateFlow<ShortcutEntity?>(null).apply {
                    viewModelScope.launch {
                        kotlinx.coroutines.flow.combine(sessionsFlow, indexFlow) { sessions, index ->
                            if (index in sessions.indices) sessions[index] else null
                        }.collect { value = it }
                    }
                }
            }
        }

    private val _isMobileView = MutableStateFlow(false)
    val isMobileView: StateFlow<Boolean> = _isMobileView.asStateFlow()

    private val _isFullscreenMode = MutableStateFlow(false)
    val isFullscreenMode: StateFlow<Boolean> = _isFullscreenMode.asStateFlow()

    // Dynamic quality adjustments for the active session
    private val _activeVncQuality = MutableStateFlow("Medium")
    val activeVncQuality: StateFlow<String> = _activeVncQuality.asStateFlow()

    private val _activeVncColorDepth = MutableStateFlow("24-bit")
    val activeVncColorDepth: StateFlow<String> = _activeVncColorDepth.asStateFlow()

    private val _activeVncScale = MutableStateFlow("Fit to screen")
    val activeVncScale: StateFlow<String> = _activeVncScale.asStateFlow()

    fun selectShortcut(shortcut: ShortcutEntity?) {
        if (shortcut == null) {
            _activeSessions.value = emptyList()
            _focusedSessionIndex.value = -1
            return
        }

        val currentSessions = _activeSessions.value.toMutableList()
        val existingIndex = currentSessions.indexOfFirst { it.id == shortcut.id && it.url == shortcut.url }

        if (existingIndex != -1) {
            _focusedSessionIndex.value = existingIndex
        } else {
            currentSessions.add(shortcut)
            _activeSessions.value = currentSessions
            _focusedSessionIndex.value = currentSessions.size - 1
        }

        _activeVncQuality.value = shortcut.vncQuality
        _activeVncColorDepth.value = shortcut.vncColorDepth
        _activeVncScale.value = shortcut.vncScale
        _isFullscreenMode.value = shortcut.isFullscreen
        
        // Save to access history
        viewModelScope.launch {
            repository.insertHistory(
                HistoryEntity(
                    shortcutId = if (shortcut.id > 0) shortcut.id else null,
                    name = shortcut.name,
                    url = shortcut.url,
                    isVnc = shortcut.isVnc,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun recordShortcutHistory(shortcut: ShortcutEntity) {
        viewModelScope.launch {
            repository.insertHistory(
                HistoryEntity(
                    shortcutId = if (shortcut.id > 0) shortcut.id else null,
                    name = shortcut.name,
                    url = shortcut.url,
                    isVnc = shortcut.isVnc,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun switchSession(index: Int) {
        if (index in _activeSessions.value.indices) {
            _focusedSessionIndex.value = index
            val shortcut = _activeSessions.value[index]
            _activeVncQuality.value = shortcut.vncQuality
            _activeVncColorDepth.value = shortcut.vncColorDepth
            _activeVncScale.value = shortcut.vncScale
            _isFullscreenMode.value = shortcut.isFullscreen
        }
    }

    fun closeSession(index: Int) {
        val currentSessions = _activeSessions.value.toMutableList()
        if (index in currentSessions.indices) {
            currentSessions.removeAt(index)
            _activeSessions.value = currentSessions
            
            val currentIndex = _focusedSessionIndex.value
            if (currentSessions.isEmpty()) {
                _focusedSessionIndex.value = -1
            } else if (currentIndex >= currentSessions.size) {
                _focusedSessionIndex.value = currentSessions.size - 1
            } else if (currentIndex == index) {
                // Focus the next available or the last one
                _focusedSessionIndex.value = if (index < currentSessions.size) index else currentSessions.size - 1
            } else if (currentIndex > index) {
                _focusedSessionIndex.value = currentIndex - 1
            }
        }
    }

    fun closeAllSessions() {
        _activeSessions.value = emptyList()
        _focusedSessionIndex.value = -1
    }

    fun launchDirectUrl(url: String, isVnc: Boolean) {
        val cleanedUrl = if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("vnc://")) {
            if (isVnc) "vnc://$url" else "http://$url"
        } else {
            url
        }

        val name = if (isVnc) "VNC Session" else "Quick Web Connect"
        val tempShortcut = ShortcutEntity(
            id = -1, // Temporary flag
            name = name,
            url = cleanedUrl,
            iconName = if (isVnc) "Desktop" else "Globe",
            isVnc = isVnc,
            vncQuality = "Medium",
            vncColorDepth = "24-bit",
            vncScale = "Fit to screen",
            isFullscreen = false
        )
        
        selectShortcut(tempShortcut)
        _activeVncQuality.value = "Medium"
        _activeVncColorDepth.value = "24-bit"
        _activeVncScale.value = "Fit to screen"
        _isFullscreenMode.value = false

        viewModelScope.launch {
            repository.insertHistory(
                HistoryEntity(
                    shortcutId = null,
                    name = name,
                    url = cleanedUrl,
                    isVnc = isVnc,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun setMobileView(mobile: Boolean) {
        _isMobileView.value = mobile
    }

    fun toggleMobileView() {
        _isMobileView.value = !_isMobileView.value
    }

    fun setFullscreenMode(fullscreen: Boolean) {
        _isFullscreenMode.value = fullscreen
    }

    fun updateActiveVncSettings(quality: String, colorDepth: String, scale: String) {
        _activeVncQuality.value = quality
        _activeVncColorDepth.value = colorDepth
        _activeVncScale.value = scale
    }

    // CRUD functions for shortcuts
    fun addShortcut(
        name: String,
        url: String,
        iconName: String,
        favIconUrl: String? = null,
        isVnc: Boolean,
        vncQuality: String = "Medium",
        vncColorDepth: String = "24-bit",
        vncScale: String = "Fit to screen",
        isFullscreen: Boolean = false,
        openInChrome: Boolean = false
    ) {
        viewModelScope.launch {
            val cleanedUrl = if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("vnc://")) {
                if (isVnc) "vnc://$url" else "http://$url"
            } else {
                url
            }
            repository.insertShortcut(
                ShortcutEntity(
                    name = name,
                    url = cleanedUrl,
                    iconName = iconName,
                    favIconUrl = favIconUrl,
                    isVnc = isVnc,
                    vncQuality = vncQuality,
                    vncColorDepth = vncColorDepth,
                    vncScale = vncScale,
                    isFullscreen = isFullscreen,
                    openInChrome = openInChrome
                )
            )
        }
    }

    fun updateShortcut(
        id: Int,
        name: String,
        url: String,
        iconName: String,
        favIconUrl: String? = null,
        isVnc: Boolean,
        vncQuality: String = "Medium",
        vncColorDepth: String = "24-bit",
        vncScale: String = "Fit to screen",
        isFullscreen: Boolean = false,
        openInChrome: Boolean = false
    ) {
        viewModelScope.launch {
            val cleanedUrl = if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("vnc://")) {
                if (isVnc) "vnc://$url" else "http://$url"
            } else {
                url
            }
            repository.updateShortcut(
                ShortcutEntity(
                    id = id,
                    name = name,
                    url = cleanedUrl,
                    iconName = iconName,
                    favIconUrl = favIconUrl,
                    isVnc = isVnc,
                    vncQuality = vncQuality,
                    vncColorDepth = vncColorDepth,
                    vncScale = vncScale,
                    isFullscreen = isFullscreen,
                    openInChrome = openInChrome
                )
            )
        }
    }

    fun deleteShortcut(shortcut: ShortcutEntity) {
        viewModelScope.launch {
            repository.deleteShortcut(shortcut)
        }
    }

    // History methods
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryById(id)
        }
    }

    fun saveWebHistory(url: String, title: String) {
        viewModelScope.launch {
            // Only save if it's a real web URL
            if (url.startsWith("http://") || url.startsWith("https://")) {
                repository.insertHistory(
                    HistoryEntity(
                        shortcutId = null,
                        name = title.ifEmpty { url },
                        url = url,
                        isVnc = false,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
