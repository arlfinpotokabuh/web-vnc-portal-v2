package com.example

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HistoryEntity
import com.example.data.ShortcutEntity
import com.example.ui.ShortcutViewModel
import com.example.ui.TransferViewModel
import com.example.ui.TransferScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    
    val viewModel: ShortcutViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return ShortcutViewModel(app) as T
        }
    })

    val transferViewModel: TransferViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return TransferViewModel(app) as T
        }
    })

    val activeShortcut by viewModel.activeShortcut.collectAsStateWithLifecycle()
    val activeSessions by viewModel.activeSessions.collectAsStateWithLifecycle()
    val focusedIndex by viewModel.focusedSessionIndex.collectAsStateWithLifecycle()
    val isFullscreenMode by viewModel.isFullscreenMode.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(0) } // 0 = Portal, 1 = Transfers & Storage
    var showTaskSwitcher by remember { mutableStateOf(false) }

    // Handle intent deep linking / App Link (like webvnc://open?url=... or standard http/https links)
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(activity?.intent) {
        val intent = activity?.intent
        intent?.data?.let { uri ->
            val urlParam = uri.getQueryParameter("url")
            val targetUrl = urlParam ?: if (uri.scheme == "webvnc") null else uri.toString()
            if (!targetUrl.isNullOrEmpty()) {
                val isVnc = uri.getQueryParameter("vnc")?.toBoolean() ?: targetUrl.startsWith("vnc")
                val name = uri.getQueryParameter("name") ?: "Web Link"
                val cleanedUrl = if (targetUrl.startsWith("vnc://")) targetUrl.replace("vnc://", "http://") else targetUrl
                
                viewModel.selectShortcut(
                    ShortcutEntity(
                        id = -999,
                        name = name,
                        url = cleanedUrl,
                        iconName = "Globe",
                        isVnc = isVnc
                    )
                )
                // Clear the intent's data so it doesn't trigger again on rotation
                intent.data = null
            }
        }
    }

    // Handle system UI fullscreen toggles
    val window = activity?.window
    LaunchedEffect(isFullscreenMode) {
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreenMode) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (activeSessions.isEmpty()) {
                NavigationBar(
                    containerColor = Color(0xFF1E1E22),
                    contentColor = Color(0xFFFF9800)
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Portal") },
                        label = { Text("Portal") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFF9800),
                            selectedTextColor = Color(0xFFFF9800),
                            indicatorColor = Color(0xFF2C2C35),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        ),
                        modifier = Modifier.testTag("tab_portal")
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        icon = { Icon(Icons.Default.Share, contentDescription = "Penyimpanan & Transfer") },
                        label = { Text("Penyimpanan & Transfer") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFFF9800),
                            selectedTextColor = Color(0xFFFF9800),
                            indicatorColor = Color(0xFF2C2C35),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        ),
                        modifier = Modifier.testTag("tab_transfers")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121214)) // Deep sleek charcoal background
        ) {
            if (activeSessions.isEmpty() || focusedIndex == -1) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "TabContentTransition"
                ) { tab ->
                    if (tab == 0) {
                        DashboardScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        TransferScreen(
                            transferViewModel = transferViewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
            
            if (activeSessions.isNotEmpty()) {
                // Multi-tasking rendering: keep sessions in memory if possible
                if (focusedIndex != -1) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        activeSessions.forEachIndexed { index, session ->
                            val isFocused = index == focusedIndex
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = if (isFocused) 1f else 0f
                                        translationX = if (isFocused) 0f else 20000f // Move out of view
                                    }
                            ) {
                                if (isFocused) {
                                    if (session.isVnc) {
                                        VncViewerScreen(
                                            shortcut = session,
                                            viewModel = viewModel,
                                            onClose = { viewModel.closeSession(index) }
                                        )
                                    } else {
                                        WebViewerScreen(
                                            shortcut = session,
                                            viewModel = viewModel,
                                            transferViewModel = transferViewModel,
                                            onClose = { viewModel.closeSession(index) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Global Multitasking Trigger
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    MultitaskingFab(onClick = { showTaskSwitcher = true })
                }
            }

            if (showTaskSwitcher) {
                TaskSwitcherOverlay(
                    activeSessions = activeSessions,
                    focusedIndex = focusedIndex,
                    onSwitch = { viewModel.switchSession(it) },
                    onClose = { viewModel.closeSession(it) },
                    onCloseAll = { viewModel.closeAllSessions() },
                    onBackToHome = { viewModel.switchSession(-1) },
                    onDismiss = { showTaskSwitcher = false }
                )
            }
        }
    }
}

// ---------------- DASHBOARD ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ShortcutViewModel,
    modifier: Modifier = Modifier
) {
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingShortcut by remember { mutableStateOf<ShortcutEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Direct connect inputs
    var quickUrl by remember { mutableStateOf("") }
    var quickIsVnc by remember { mutableStateOf(false) }

    val filteredShortcuts = shortcuts.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Subtle glowing background ambient spots
                drawCircle(
                    color = Color(0x0CFF9800),
                    radius = 350.dp.toPx(),
                    center = Offset(size.width * 0.8f, size.height * 0.1f)
                )
                drawCircle(
                    color = Color(0x0C00BCD4),
                    radius = 400.dp.toPx(),
                    center = Offset(size.width * 0.1f, size.height * 0.8f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Web & VNC Portal",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Akses cepat web & remote desktop",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.Gray
                        )
                    )
                }
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .background(Color(0xFF232329), CircleShape)
                        .testTag("add_shortcut_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Tambah Shortcut",
                        tint = Color(0xFFFF9800)
                    )
                }
            }

            // Quick Connection Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2C2C35)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Koneksi Cepat (Quick Connect)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = quickUrl,
                            onValueChange = { quickUrl = it },
                            placeholder = { Text("IP:Port atau Alamat Web") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_connect_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF151518),
                                unfocusedContainerColor = Color(0xFF151518),
                                focusedBorderColor = Color(0xFFFF9800),
                                unfocusedBorderColor = Color(0xFF33333C)
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                if (quickUrl.isNotBlank()) {
                                    viewModel.launchDirectUrl(quickUrl, quickIsVnc)
                                }
                            })
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (quickUrl.isNotBlank()) {
                                    viewModel.launchDirectUrl(quickUrl, quickIsVnc)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (quickIsVnc) Color(0xFF00BCD4) else Color(0xFFFF9800)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("quick_connect_go_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Buka", tint = Color.Black)
                        }
                    }

                    // Toggle Button Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mode Koneksi:",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray),
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        FilterChip(
                            selected = !quickIsVnc,
                            onClick = { quickIsVnc = false },
                            label = { Text("Web (HTTP/HTTPS)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0x26FF9800),
                                selectedLabelColor = Color(0xFFFF9800),
                                containerColor = Color(0xFF151518),
                                labelColor = Color.Gray
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = quickIsVnc,
                            onClick = { quickIsVnc = true },
                            label = { Text("VNC Remote") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0x2600BCD4),
                                selectedLabelColor = Color(0xFF00BCD4),
                                containerColor = Color(0xFF151518),
                                labelColor = Color.Gray
                            )
                        )
                    }
                }
            }

            // Stats row (Connections / History count)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0x1AFF9800), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFF9800))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Shortcuts", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                            Text("${shortcuts.size} disimpan", style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0x1A00BCD4), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00BCD4))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Riwayat", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
                            Text("${history.size} sesi", style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Tabs / Layouts
            var selectedTab by remember { mutableStateOf(0) } // 0 = Saved, 1 = History
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFFFF9800),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Koneksi Disimpan", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Riwayat Akses", fontWeight = FontWeight.SemiBold) }
                )
            }

            // Search Bar (Only for saved shortcuts)
            if (selectedTab == 0) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Cari shortcut...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151518),
                        unfocusedContainerColor = Color(0xFF151518),
                        focusedBorderColor = Color(0xFFFF9800),
                        unfocusedBorderColor = Color(0xFF2C2C35)
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    singleLine = true
                )
            }

            // Content List
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    if (filteredShortcuts.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isEmpty()) "Belum ada shortcut disimpan.\nKlik tombol '+' di atas untuk menambah." else "Shortcut tidak ditemukan.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 80.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredShortcuts) { shortcut ->
                                ShortcutCard(
                                    shortcut = shortcut,
                                    onConnect = { viewModel.selectShortcut(shortcut) },
                                    onEdit = { editingShortcut = shortcut },
                                    onDelete = { viewModel.deleteShortcut(shortcut) }
                                )
                            }
                        }
                    }
                } else {
                    if (history.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color.DarkGray
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Belum ada riwayat koneksi.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.clearHistory() }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Hapus Semua", color = Color.Red, fontSize = 12.sp)
                                }
                            }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(history) { item ->
                                    HistoryItemRow(
                                        item = item,
                                        onConnect = {
                                            viewModel.launchDirectUrl(item.url, item.isVnc)
                                        },
                                        onDelete = { viewModel.deleteHistoryItem(item.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Dialog
        if (showAddDialog) {
            AddEditShortcutDialog(
                onDismiss = { showAddDialog = false },
                onSave = { name, url, icon, favIcon, isVnc, vncQ, vncC, vncS, fs ->
                    viewModel.addShortcut(name, url, icon, favIcon, isVnc, vncQ, vncC, vncS, fs)
                    showAddDialog = false
                }
            )
        }

        // Edit Dialog
        if (editingShortcut != null) {
            AddEditShortcutDialog(
                shortcut = editingShortcut,
                onDismiss = { editingShortcut = null },
                onSave = { name, url, icon, favIcon, isVnc, vncQ, vncC, vncS, fs ->
                    editingShortcut?.let {
                        viewModel.updateShortcut(it.id, name, url, icon, favIcon, isVnc, vncQ, vncC, vncS, fs)
                    }
                    editingShortcut = null
                }
            )
        }
    }
}

// ---------------- SHORTCUT CARD & LISTS ----------------

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ShortcutCard(
    shortcut: ShortcutEntity,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onConnect() },
                onLongClick = { showMenu = true }
            )
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag("shortcut_card_${shortcut.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(
                    color = if (shortcut.isVnc) Color(0x1A00BCD4) else Color(0x1AFF9800),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            ShortcutIcon(
                iconName = shortcut.iconName,
                favIconUrl = shortcut.favIconUrl,
                tint = if (shortcut.isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800),
                modifier = Modifier.size(32.dp)
            )
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(Color(0xFF232329))
            ) {
                DropdownMenuItem(
                    text = { Text("Bagikan Link", color = Color.White) },
                    onClick = {
                        showMenu = false
                        try {
                            val domain = "ais-pre-qrblnyd3cw4asxasjh7lml-915540977151.asia-southeast1.run.app"
                            val encodedUrl = java.net.URLEncoder.encode(shortcut.url, "UTF-8")
                            val encodedName = java.net.URLEncoder.encode(shortcut.name, "UTF-8")
                            val shareUrl = "https://$domain/open?url=$encodedUrl&name=$encodedName&vnc=${shortcut.isVnc}"
                            
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Portal App Link", shareUrl)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "Link portal disalin!", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Gagal menyalin link", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("Edit", color = Color.White) },
                    onClick = {
                        showMenu = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp)) }
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = Color.Red) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = shortcut.name,
            style = MaterialTheme.typography.labelMedium.copy(
                color = Color.White,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun HistoryItemRow(
    item: HistoryEntity,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss - dd MMM", Locale.getDefault()) }
    val formattedTime = formatter.format(Date(item.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() }
            .testTag("history_item_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (item.isVnc) Color(0x1A00BCD4) else Color(0x1AFF9800),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isVnc) Icons.Default.Settings else Icons.Default.Home,
                        contentDescription = null,
                        tint = if (item.isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = item.url,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray, fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.DarkGray, fontSize = 9.sp)
                    )
                }
            }
            IconButton(onClick = { onDelete() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ---------------- DIALOG ADD/EDIT ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditShortcutDialog(
    shortcut: ShortcutEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, Boolean, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(shortcut?.name ?: "") }
    var url by remember { mutableStateOf(shortcut?.url ?: "") }
    var selectedIcon by remember { mutableStateOf(shortcut?.iconName ?: "Globe") }
    var favIconUrl by remember { mutableStateOf(shortcut?.favIconUrl) }
    var isVnc by remember { mutableStateOf(shortcut?.isVnc ?: false) }
    var vncQuality by remember { mutableStateOf(shortcut?.vncQuality ?: "Medium") }
    var vncColorDepth by remember { mutableStateOf(shortcut?.vncColorDepth ?: "24-bit") }
    var vncScale by remember { mutableStateOf(shortcut?.vncScale ?: "Fit to screen") }
    var isFullscreen by remember { mutableStateOf(shortcut?.isFullscreen ?: false) }

    // Automatic favicon detection
    LaunchedEffect(url, isVnc) {
        if (!isVnc && url.isNotBlank()) {
            val domain = try {
                val cleanedUrl = if (!url.startsWith("http")) "https://$url" else url
                java.net.URL(cleanedUrl).host?.removePrefix("www.")
            } catch (e: Exception) {
                null
            }
            if (domain != null && domain.contains(".")) {
                favIconUrl = "https://www.google.com/s2/favicons?sz=128&domain=$domain"
            } else {
                favIconUrl = null
            }
        } else {
            favIconUrl = null
        }
    }

    val iconsList = listOf("Link", "Globe", "Desktop", "Terminal", "Cloud", "Star", "Download", "Movie", "Music", "Security", "Camera", "Settings")

    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF232329)),
            border = BorderStroke(1.dp, Color(0xFF33333C)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (shortcut == null) "Tambah Shortcut Baru" else "Edit Shortcut",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Connection Type Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                isVnc = false
                                if (selectedIcon == "Desktop") selectedIcon = "Globe"
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isVnc) Color(0x33FF9800) else Color(0xFF151518)
                        ),
                        border = BorderStroke(1.dp, if (!isVnc) Color(0xFFFF9800) else Color.Transparent)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = if (!isVnc) Color(0xFFFF9800) else Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Web (HTTP/S)", color = if (!isVnc) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                isVnc = true
                                selectedIcon = "Desktop"
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isVnc) Color(0x3300BCD4) else Color(0xFF151518)
                        ),
                        border = BorderStroke(1.dp, if (isVnc) Color(0xFF00BCD4) else Color.Transparent)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = if (isVnc) Color(0xFF00BCD4) else Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("VNC Remote", color = if (isVnc) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Shortcut") },
                    placeholder = { Text("misal: Proxmox Web, PC Kantor") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151518),
                        unfocusedContainerColor = Color(0xFF151518),
                        focusedLabelColor = if (isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800),
                        focusedBorderColor = if (isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800),
                        unfocusedBorderColor = Color(0xFF33333C)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("shortcut_name_input")
                )

                // URL field
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Alamat IP / Port atau Web Link") },
                    placeholder = { Text(if (isVnc) "192.168.1.100:5900" else "https://google.com") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151518),
                        unfocusedContainerColor = Color(0xFF151518),
                        focusedLabelColor = if (isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800),
                        focusedBorderColor = if (isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800),
                        unfocusedBorderColor = Color(0xFF33333C)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("shortcut_url_input")
                )

                // Fullscreen switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Layar Penuh (Fullscreen)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Sembunyikan status bar otomatis saat dibuka", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isFullscreen,
                        onCheckedChange = { isFullscreen = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = if (isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800),
                            checkedTrackColor = if (isVnc) Color(0x6600BCD4) else Color(0x66FF9800)
                        )
                    )
                }

                // VNC Quality Settings
                if (isVnc) {
                    Divider(color = Color(0xFF33333C), modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        "Konfigurasi Kualitas Gambar/Video VNC",
                        color = Color(0xFF00BCD4),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Quality Selector
                    Text("Kualitas:", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Low", "Medium", "High").forEach { q ->
                            val isSelected = vncQuality == q
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) Color(0x3300BCD4) else Color(0xFF151518),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF00BCD4) else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { vncQuality = q }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = q,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Color Depth Selector
                    Text("Kedalaman Warna:", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("8-bit", "16-bit", "24-bit").forEach { depth ->
                            val isSelected = vncColorDepth == depth
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) Color(0x3300BCD4) else Color(0xFF151518),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF00BCD4) else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { vncColorDepth = depth }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = depth,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Scaling Selector
                    Text("Skala Tampilan:", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Fit to screen", "Original").forEach { scale ->
                            val isSelected = vncScale == scale
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) Color(0x3300BCD4) else Color(0xFF151518),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF00BCD4) else Color.Transparent,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { vncScale = scale }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = scale,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Divider(color = Color(0xFF33333C), modifier = Modifier.padding(vertical = 12.dp))

                // Icon Picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pilih Icon Tampilan:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    
                    if (favIconUrl != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Favicon Terdeteksi", color = Color(0xFFFF9800), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            ShortcutIcon(iconName = "", favIconUrl = favIconUrl, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconsList.forEach { iconName ->
                        val isSelected = selectedIcon == iconName && favIconUrl == null
                        val tint = if (isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800)
                        val bg = if (isSelected) {
                            if (isVnc) Color(0x3300BCD4) else Color(0x33FF9800)
                        } else Color(0xFF151518)

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(bg, RoundedCornerShape(8.dp))
                                .border(1.dp, if (isSelected) tint else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { 
                                    selectedIcon = iconName
                                    if (iconName != "Link") {
                                        // If user manually picks an icon, we might want to keep favicon or not
                                        // For now, let's say favicon always takes precedence if detected, 
                                        // but user can override by picking a manual icon?
                                        // Actually, let's clear favicon if they pick a manual icon?
                                        // No, user said "automatic", so let's keep it simple.
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                        ShortcutIcon(
                            iconName = iconName,
                            tint = if (isSelected) tint else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onDismiss() }) {
                        Text("Batal", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && url.isNotBlank()) {
                                onSave(name, url, selectedIcon, favIconUrl, isVnc, vncQuality, vncColorDepth, vncScale, isFullscreen)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800)
                        ),
                        enabled = name.isNotBlank() && url.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("save_shortcut_button")
                    ) {
                        Text("Simpan", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- MULTITASKING UI ----------------

@Composable
fun TaskSwitcherOverlay(
    activeSessions: List<ShortcutEntity>,
    focusedIndex: Int,
    onSwitch: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onCloseAll: () -> Unit,
    onBackToHome: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF2C2C35))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tugas Aktif",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (activeSessions.isEmpty()) {
                    Text("Tidak ada tugas aktif", color = Color.Gray, modifier = Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(activeSessions) { index, session ->
                            val isFocused = index == focusedIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isFocused) Color(0x1A00BCD4) else Color(0xFF151518),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isFocused) Color(0xFF00BCD4) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { 
                                        onSwitch(index)
                                        onDismiss()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ShortcutIcon(
                                    iconName = session.iconName,
                                    favIconUrl = session.favIconUrl,
                                    tint = if (session.isVnc) Color(0xFF00BCD4) else Color(0xFFFF9800),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        session.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        session.url,
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onClose(index) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Tutup Tugas", tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onBackToHome()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFF2C2C35)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Beranda", fontSize = 12.sp)
                    }
                    
                    Button(
                        onClick = onCloseAll,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tutup Semua", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MultitaskingFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = Color(0xFF2C2C35),
        contentColor = Color(0xFFFF9800),
        shape = CircleShape,
        modifier = Modifier
            .padding(16.dp)
            .size(48.dp)
    ) {
        Icon(Icons.Default.Layers, contentDescription = "Multitasking")
    }
}

@Composable
fun ShortcutIcon(iconName: String, tint: Color, modifier: Modifier = Modifier, favIconUrl: String? = null) {
    if (favIconUrl != null) {
        AsyncImage(
            model = favIconUrl,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(4.dp))
        )
    } else if (iconName == "Link") {
        Image(
            painter = painterResource(id = R.drawable.ic_shortcut_link),
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(4.dp))
        )
    } else {
        Icon(
            imageVector = getIconByName(iconName),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}

fun getIconByName(name: String): ImageVector {
    return when (name) {
        "Link" -> Icons.Default.Link
        "Globe" -> Icons.Default.Home
        "Desktop" -> Icons.Default.Monitor
        "Terminal" -> Icons.Default.Terminal
        "Cloud" -> Icons.Default.Cloud
        "Star" -> Icons.Default.Star
        "Download" -> Icons.Default.GetApp
        "Movie" -> Icons.Default.Movie
        "Music" -> Icons.Default.MusicNote
        "Security" -> Icons.Default.Security
        "Camera" -> Icons.Default.PhotoCamera
        "Settings" -> Icons.Default.Settings
        else -> Icons.Default.Home
    }
}

// ---------------- WEB VIEWER (WITH IFRAME ALLOWANCE AND NAVIGATION) ----------------

@Composable
fun WebViewerScreen(
    shortcut: ShortcutEntity,
    viewModel: ShortcutViewModel,
    transferViewModel: TransferViewModel,
    onClose: () -> Unit
) {
    val isMobileView by viewModel.isMobileView.collectAsStateWithLifecycle()
    val isFullscreenMode by viewModel.isFullscreenMode.collectAsStateWithLifecycle()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var currentUrl by remember { mutableStateOf(shortcut.url) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var showController by remember { mutableStateOf(true) }
    var detectedVideos by remember { mutableStateOf(listOf<Pair<String, String>>()) } // List of (Url, Title)
    var showDetectionNotification by remember { mutableStateOf(false) }

    // Draggable Bubble State
    var bubbleOffset by remember { mutableStateOf(IntOffset(0, 0)) }
    var isBubbleVisible by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // WebView instance
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Enable Cookies Persistence
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(WebView(ctx), true)

                WebView(ctx).apply {
                    webViewRef = this
                    setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                        val filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype) ?: "download_file"
                        transferViewModel.addDownload(url, filename)
                        android.widget.Toast.makeText(ctx, "Mengunduh file: $filename", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    
                    // Add JS Interface for video playback detection
                    addJavascriptInterface(
                        object {
                            @android.webkit.JavascriptInterface
                            fun onVideoDetected(src: String, title: String) {
                                scope.launch {
                                    if (!detectedVideos.any { it.first == src }) {
                                        detectedVideos = detectedVideos + (src to title)
                                        showDetectionNotification = true
                                    }
                                }
                            }

                            @android.webkit.JavascriptInterface
                            fun onScanFinished(count: Int) {
                                scope.launch {
                                    if (count == 0) {
                                        android.widget.Toast.makeText(ctx, "Tidak ada video ditemukan", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(ctx, "Berhasil menemukan $count video!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        "VideoDetector"
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowContentAccess = true
                        allowFileAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        cacheMode = WebSettings.LOAD_DEFAULT
                        
                        // Apply mobile/desktop UA initially inside the settings apply
                        userAgentString = if (isMobileView) {
                            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                        } else {
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36"
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let { 
                                currentUrl = it
                                viewModel.saveWebHistory(it, view?.title ?: "")
                            }
                            canGoBack = view?.canGoBack() ?: false
                            canGoForward = view?.canGoForward() ?: false
                            
                            // Inject auto video detection scanner
                            view?.evaluateJavascript("""
                                (function() {
                                    if (window.videoDetectionInterval) return;
                                    
                                    var videoStatus = new Map();
                                    
                                    window.videoDetectionInterval = setInterval(function() {
                                        var videos = document.getElementsByTagName('video');
                                        var now = Date.now();
                                        
                                        for (var i = 0; i < videos.length; i++) {
                                            var video = videos[i];
                                            var src = video.currentSrc || video.src;
                                            if (!src) continue;
                                            
                                            if (!videoStatus.has(video) || videoStatus.get(video).src !== src) {
                                                videoStatus.set(video, {
                                                    src: src,
                                                    accumulatedPlayTime: 0,
                                                    lastUpdate: now,
                                                    triggered: false
                                                });
                                            }
                                            
                                            var state = videoStatus.get(video);
                                            
                                            if (!video.paused && !video.ended && video.readyState >= 2) {
                                                var delta = now - state.lastUpdate;
                                                state.accumulatedPlayTime += delta;
                                                state.lastUpdate = now;
                                                
                                                if (state.accumulatedPlayTime >= 5000 && !state.triggered) {
                                                    if (window.VideoDetector) {
                                                        state.triggered = true;
                                                        window.VideoDetector.onVideoDetected(src, document.title || "Video");
                                                    }
                                                }
                                            } else {
                                                state.lastUpdate = now;
                                            }
                                        }
                                    }, 1000);

                                    // Manual scan function
                                    window.manualVideoScan = function() {
                                        var count = 0;
                                        var foundUrls = new Set();

                                        function checkAndNotify(src, title) {
                                            if (!src || typeof src !== 'string') return;
                                            if (src.startsWith('//')) src = 'https:' + src;
                                            if (src.startsWith('/') && !src.startsWith('//')) src = window.location.origin + src;
                                            
                                            if (src && !foundUrls.has(src)) {
                                                foundUrls.add(src);
                                                count++;
                                                if (window.VideoDetector) {
                                                    window.VideoDetector.onVideoDetected(src, title);
                                                }
                                            }
                                        }

                                        // 1. Search video tags
                                        var videos = document.getElementsByTagName('video');
                                        for (var i = 0; i < videos.length; i++) {
                                            var src = videos[i].currentSrc || videos[i].src;
                                            checkAndNotify(src, document.title || "Video Scan");
                                        }

                                        // 2. Search source tags
                                        var sources = document.getElementsByTagName('source');
                                        for (var j = 0; j < sources.length; j++) {
                                            checkAndNotify(sources[j].src, document.title || "Video Source Scan");
                                        }

                                        // 2b. Search iframe sources
                                        var iframes = document.getElementsByTagName('iframe');
                                        for (var k = 0; k < iframes.length; k++) {
                                            var isrc = iframes[k].src;
                                            if (isrc && (isrc.includes('.mp4') || isrc.includes('.m3u8') || isrc.includes('player'))) {
                                                checkAndNotify(isrc, "Iframe: " + (document.title || "Video"));
                                            }
                                        }

                                        // 3. Search for common video extensions in all tags with src/href/data
                                        var all = document.querySelectorAll('[src], [href], [data-src], [data-video], [data-url], [data-file]');
                                        var videoExts = ['.mp4', '.m3u8', '.webm', '.mov', '.avi', '.mpd', 'manifest', 'googlevideo.com', 'playlist'];
                                        all.forEach(el => {
                                            var url = el.src || el.href || el.getAttribute('data-src') || el.getAttribute('data-video') || el.getAttribute('data-url') || el.getAttribute('data-file');
                                            if (typeof url === 'string') {
                                                var lower = url.toLowerCase();
                                                if (videoExts.some(ext => lower.includes(ext))) {
                                                    checkAndNotify(url, document.title || "Potential Video");
                                                }
                                            }
                                        });

                                        if (window.VideoDetector) {
                                            window.VideoDetector.onScanFinished(count);
                                        }
                                        return count;
                                    };
                                })();
                            """.trimIndent(), null)
                        }

                        // THE MAGICAL INTERCEPTOR FOR BYPASSING IFRAME X-FRAME-OPTIONS & CSP FOR ALL POPULAR WEBSITES
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            if (request == null) return null
                            val urlStr = request.url.toString()
                            val lowerUrl = urlStr.lowercase()

                            // NETWORK-LEVEL VIDEO DETECTION
                            val videoExtensions = listOf(".mp4", ".m3u8", ".webm", ".mov", ".avi", ".ts", ".mpd", "manifest", "playlist.m3u8", "chunklist", "get_video", "googlevideo", "stream")
                            if (videoExtensions.any { lowerUrl.contains(it) } && 
                                !lowerUrl.contains(".js") && !lowerUrl.contains(".css") && !lowerUrl.contains(".png") && !lowerUrl.contains(".jpg") && !lowerUrl.contains(".woff")) {
                                android.util.Log.d("VideoDetection", "Detected URL: $urlStr")
                                scope.launch {
                                    if (!detectedVideos.any { it.first == urlStr }) {
                                        val title = view?.title ?: "Video Terdeteksi"
                                        detectedVideos = (detectedVideos + (urlStr to title)).distinctBy { it.first }
                                        showDetectionNotification = true
                                    }
                                }
                            }
                            
                            // To prevent blocking local or secure traffic unnecessarily, we only intercept HTML/Document requests
                            val isHtmlRequest = request.method.equals("GET", ignoreCase = true) && 
                                    (request.isForMainFrame || urlStr.contains(".html") || !urlStr.substringAfterLast("/", "").contains("."))
                            
                            if (isHtmlRequest && (urlStr.startsWith("http://") || urlStr.startsWith("https://"))) {
                                try {
                                    val client = OkHttpClient.Builder()
                                        .followRedirects(true)
                                        .followSslRedirects(true)
                                        .build()

                                    val reqBuilder = Request.Builder().url(urlStr)
                                    request.requestHeaders.forEach { (k, v) ->
                                        reqBuilder.addHeader(k, v)
                                    }
                                    
                                    // Set dynamic User-Agent in subrequests
                                    reqBuilder.header("User-Agent", view?.settings?.userAgentString ?: "")

                                    val response = client.newCall(reqBuilder.build()).execute()
                                    val body = response.body
                                    if (body != null) {
                                        val contentType = body.contentType()
                                        val mimeType = contentType?.type + "/" + contentType?.subtype
                                        val encoding = contentType?.charset()?.name() ?: "UTF-8"
                                        
                                        // MODIFICATION: Inject our JS into every HTML response to ensure it reaches IFRAMES!
                                        var html = body.string()
                                        val injectionScript = """
                                            <script>
                                            (function() {
                                                if (window.videoDetectionInterval) return;
                                                var videoStatus = new Map();
                                                window.videoDetectionInterval = setInterval(function() {
                                                    var videos = document.getElementsByTagName('video');
                                                    var now = Date.now();
                                                    for (var i = 0; i < videos.length; i++) {
                                                        var video = videos[i];
                                                        var src = video.currentSrc || video.src;
                                                        if (!src) continue;
                                                        if (!videoStatus.has(video) || videoStatus.get(video).src !== src) {
                                                            videoStatus.set(video, { src: src, accumulatedPlayTime: 0, lastUpdate: now, triggered: false });
                                                        }
                                                        var state = videoStatus.get(video);
                                                        if (!video.paused && !video.ended && video.readyState >= 2) {
                                                            var delta = now - state.lastUpdate;
                                                            state.accumulatedPlayTime += delta;
                                                            state.lastUpdate = now;
                                                            if (state.accumulatedPlayTime >= 5000 && !state.triggered) {
                                                                if (window.VideoDetector) {
                                                                    state.triggered = true;
                                                                    window.VideoDetector.onVideoDetected(src, document.title || "Video");
                                                                }
                                                            }
                                                        } else {
                                                            state.lastUpdate = now;
                                                        }
                                                    }
                                                }, 1000);
                                            })();
                                            </script>
                                        """.trimIndent()
                                        
                                        if (html.contains("<head>", ignoreCase = true)) {
                                            html = html.replace("<head>", "<head>$injectionScript", ignoreCase = true)
                                        } else if (html.contains("<html>", ignoreCase = true)) {
                                            html = html.replace("<html>", "<html>$injectionScript", ignoreCase = true)
                                        } else {
                                            html = injectionScript + html
                                        }

                                        // FILTER HEADERS: Strip CSP and X-Frame-Options to allow framing/iframes!
                                        val headersMap = mutableMapOf<String, String>()
                                        response.headers.forEach { pair ->
                                            val lowerKey = pair.first.lowercase(Locale.ROOT)
                                            if (lowerKey != "x-frame-options" &&
                                                lowerKey != "content-security-policy" &&
                                                lowerKey != "content-security-policy-report-only" &&
                                                lowerKey != "frame-options"
                                            ) {
                                                headersMap[pair.first] = pair.second
                                            }
                                        }

                                        // Enable CORS for everything we intercept
                                        headersMap["Access-Control-Allow-Origin"] = "*"
                                        headersMap["Access-Control-Allow-Headers"] = "*"

                                        return WebResourceResponse(
                                            mimeType,
                                            encoding,
                                            response.code,
                                            "OK",
                                            headersMap,
                                            html.byteInputStream()
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            return null
                        }
                    }

                    loadUrl(shortcut.url)
                }
            },
            update = { webView ->
                // Dynamically update user agent if desktop/mobile view toggles
                val expectedUa = if (isMobileView) {
                    "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
                } else {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36"
                }
                if (webView.settings.userAgentString != expectedUa) {
                    webView.settings.userAgentString = expectedUa
                    webView.reload()
                }
            }
        )

        // Floating Progress Bar at top
        if (progress < 100) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.TopCenter),
                color = Color(0xFFFF9800),
                trackColor = Color.Transparent
            )
        }

        // Sliding Notification Overlay for Detected Video
        AnimatedVisibility(
            visible = showDetectionNotification && detectedVideos.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFFF9800)),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x33FF9800), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GetApp,
                                contentDescription = null,
                                tint = Color(0xFFFF9800)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Video Terdeteksi! (${detectedVideos.size})",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            val lastVideo = detectedVideos.lastOrNull()
                            Text(
                                text = lastVideo?.second ?: "Siap diunduh",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { 
                                showDetectionNotification = false
                                detectedVideos = emptyList() // Clear once tutup
                            }
                        ) {
                            Text("Tutup", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                detectedVideos.forEach { (url, title) ->
                                    val guessName = android.webkit.URLUtil.guessFileName(url, null, null) ?: "video_download.mp4"
                                    val finalName = if (guessName.endsWith(".bin") || !guessName.contains(".")) "video_download.mp4" else guessName
                                    transferViewModel.addDownload(url, finalName)
                                }
                                android.widget.Toast.makeText(context, "Mulai mengunduh ${detectedVideos.size} video...", android.widget.Toast.LENGTH_SHORT).show()
                                detectedVideos = emptyList()
                                showDetectionNotification = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Unduh Semua", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- Draggable Bubble Icon ---
        if (isBubbleVisible) {
            Box(
                modifier = Modifier
                    .offset { bubbleOffset }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            bubbleOffset = IntOffset(
                                (bubbleOffset.x + dragAmount.x).roundToInt(),
                                (bubbleOffset.y + dragAmount.y).roundToInt()
                            )
                        }
                    }
                    .size(56.dp)
                    .padding(8.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                        ),
                        shape = CircleShape
                    )
                    .clickable {
                        // Manual Scan on click bubble
                        webViewRef?.evaluateJavascript("if(window.manualVideoScan) window.manualVideoScan();", null)
                    }
                    .align(Alignment.CenterEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Scan Video",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
 
        // Overlay floating handle controller
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Quick Toggle Tab to open/hide controller
                IconButton(
                    onClick = { showController = !showController },
                    modifier = Modifier
                        .background(Color(0xE6232329), RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        .size(40.dp, 24.dp)
                ) {
                    Icon(
                        imageVector = if (showController) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle Control",
                        tint = Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }
 
                AnimatedVisibility(
                    visible = showController,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xE61E1E22)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF33333C)),
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Back Button
                            IconButton(
                                onClick = { webViewRef?.goBack() },
                                enabled = canGoBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = if (canGoBack) Color.White else Color.DarkGray)
                            }
 
                            // Forward Button
                            IconButton(
                                onClick = { webViewRef?.goForward() },
                                enabled = canGoForward,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Maju", tint = if (canGoForward) Color.White else Color.DarkGray)
                            }
 
                            // Refresh Button
                            IconButton(
                                onClick = { webViewRef?.reload() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Muat Ulang", tint = Color.White)
                            }

                            // Toggle Bubble Visibility
                            IconButton(
                                onClick = { isBubbleVisible = !isBubbleVisible },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBubbleVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Bubble",
                                    tint = if (isBubbleVisible) Color(0xFFFF9800) else Color.Gray
                                )
                            }
 
                            // Desktop/Mobile toggle
                            IconButton(
                                onClick = { viewModel.setMobileView(!isMobileView) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(if (!isMobileView) Color(0x33FF9800) else Color.Transparent, RoundedCornerShape(6.dp))
                            ) {
                                Icon(
                                    imageVector = if (isMobileView) Icons.Default.Warning else Icons.Default.Settings,
                                    contentDescription = "Ubah Tampilan Desktop/Mobile",
                                    tint = if (!isMobileView) Color(0xFFFF9800) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
 
                            // Fullscreen Toggle
                            IconButton(
                                onClick = { viewModel.setFullscreenMode(!isFullscreenMode) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(if (isFullscreenMode) Color(0x334CAF50) else Color.Transparent, RoundedCornerShape(6.dp))
                            ) {
                                Icon(
                                    imageVector = if (isFullscreenMode) Icons.Default.Close else Icons.Default.Settings,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = if (isFullscreenMode) Color(0xFF4CAF50) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
 
                            // Divider
                            Box(
                                modifier = Modifier
                                    .height(24.dp)
                                    .width(1.dp)
                                    .background(Color.DarkGray)
                            )
 
                            // Exit Viewer Home Button
                            IconButton(
                                onClick = { onClose() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0x33F44336), RoundedCornerShape(6.dp))
                            ) {
                                Icon(Icons.Default.Home, contentDescription = "Portal Utama", tint = Color(0xFFF44336))
                            }
                        }
                    }
                }
            }
        }
    }
}


// ---------------- INTERACTIVE VNC VIEW REMOTE DESKTOP SIMULATOR ----------------

@Composable
fun VncViewerScreen(
    shortcut: ShortcutEntity,
    viewModel: ShortcutViewModel,
    onClose: () -> Unit
) {
    val quality by viewModel.activeVncQuality.collectAsStateWithLifecycle()
    val colorDepth by viewModel.activeVncColorDepth.collectAsStateWithLifecycle()
    val scale by viewModel.activeVncScale.collectAsStateWithLifecycle()
    val isFullscreenMode by viewModel.isFullscreenMode.collectAsStateWithLifecycle()

    var showControlPanel by remember { mutableStateOf(true) }
    val connectionLogs = remember { mutableStateListOf<String>() }
    var isConnected by remember { mutableStateOf(false) }
    var bandwidthRate by remember { mutableStateOf("0 KB/s") }
    var currentFps by remember { mutableStateOf(0) }

    // Remote desktop simulation workspace elements
    var mouseX by remember { mutableStateOf(300f) }
    var mouseY by remember { mutableStateOf(200f) }
    var terminalInputText by remember { mutableStateOf("") }
    var showTerminalWindow by remember { mutableStateOf(false) }
    var showSystemInfoWindow by remember { mutableStateOf(false) }
    val terminalOutputLines = remember { mutableStateListOf<String>() }

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Establish VNC simulation logs handshake
    LaunchedEffect(shortcut) {
        connectionLogs.clear()
        isConnected = false
        delay(300)
        connectionLogs.add("Initiating connection to ${shortcut.url}...")
        delay(600)
        connectionLogs.add("Establishing RFB protocol handshake...")
        delay(500)
        connectionLogs.add("Remote RFB version: RFB 003.008")
        delay(400)
        connectionLogs.add("Negotiated Security Types: Standard VNC Auth (Type 2)")
        delay(700)
        connectionLogs.add("Authentication successfully completed (Anonymous)")
        delay(500)
        connectionLogs.add("Requesting Framebuffer update size 1024x768...")
        delay(400)
        connectionLogs.add("RFB Framebuffer session active. Connected successfully!")
        isConnected = true

        // Push startup history lines to terminal output
        terminalOutputLines.add("Welcome to NetLink Terminal session v1.0.0")
        terminalOutputLines.add("type 'help' for available commands.")
        terminalOutputLines.add("user@vnc-desktop:~$ ")
    }

    // Performance Stats generator loop
    LaunchedEffect(quality, isConnected) {
        if (isConnected) {
            while (true) {
                // Dynamically change statistics based on VNC quality settings
                when (quality) {
                    "Low" -> {
                        bandwidthRate = "${(80..130).random()} KB/s"
                        currentFps = (12..16).random()
                    }
                    "Medium" -> {
                        bandwidthRate = "${(400..650).random()} KB/s"
                        currentFps = (28..32).random()
                    }
                    "High" -> {
                        bandwidthRate = "${(1800..2600).random()} KB/s (2.4 MB/s)"
                        currentFps = (58..60).random()
                    }
                }
                delay(1200)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!isConnected) {
            // Handshake Loading Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color(0xFF00BCD4))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "MENGHUBUNGKAN KE DESTOP VNC",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00BCD4),
                    fontSize = 14.sp
                )
                Text(
                    shortcut.url,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Connection logs container
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151518)),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF232329)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(connectionLogs) { log ->
                            Text(
                                ">>> $log",
                                fontFamily = FontFamily.Monospace,
                                color = Color.Green,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Full interactive VNC Canvas Workspace
            // Desktop screen canvas size is designed for fit/original scale
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            mouseX = (mouseX + dragAmount.x).coerceIn(0f, size.width.toFloat())
                            mouseY = (mouseY + dragAmount.y).coerceIn(0f, size.height.toFloat())
                        }
                    }
            ) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight

                // Custom Color Filters based on color-depth and quality simulation
                val scaleFactor = if (scale == "Fit to screen") 1.0f else 1.3f
                val colorMatrix = remember(quality, colorDepth) {
                    when (quality) {
                        "Low" -> {
                            // High contrast and reduced color palette simulation (pixelated style)
                            ColorMatrix().apply {
                                setToSaturation(0.5f)
                            }
                        }
                        "Medium" -> {
                            ColorMatrix().apply {
                                setToSaturation(0.85f)
                            }
                        }
                        else -> ColorMatrix() // Crystal true color
                    }
                }

                // REMOTE DESKTOP CANVAS BACKGROUND AND SIMULATOR
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // Desktop Gradient Background
                            val brush = if (quality == "Low") {
                                // Low Quality: banding color simulation
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0C2540), Color(0xFF0B1425))
                                )
                            } else {
                                // High Quality: Beautiful royal premium terminal desktop background
                                Brush.radialGradient(
                                    colors = listOf(Color(0xFF1E3A5F), Color(0xFF0F172A)),
                                    center = Offset(size.width / 2, size.height / 2),
                                    radius = size.width
                                )
                            }
                            drawRect(brush = brush)

                            // Low quality pixel grid overlay
                            if (quality == "Low") {
                                val dotSpacing = 8.dp.toPx()
                                for (x in 0 until (size.width / dotSpacing).toInt()) {
                                    for (y in 0 until (size.height / dotSpacing).toInt()) {
                                        drawCircle(
                                            color = Color(0x10FFFFFF),
                                            radius = 1.dp.toPx(),
                                            center = Offset(x * dotSpacing, y * dotSpacing)
                                        )
                                    }
                                }
                            }
                        }
                ) {
                    // Desktop Shortcut Icons inside VNC Desktop
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .align(Alignment.TopStart),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Desktop Terminal Shortcut
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showTerminalWindow = true }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0x33000000), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Green, modifier = Modifier.size(28.dp))
                            }
                            Text("Terminal", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        }

                        // Desktop System Info Shortcut
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showSystemInfoWindow = true }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0x33000000), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00BCD4), modifier = Modifier.size(28.dp))
                            }
                            Text("SysInfo", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        }
                    }

                    // --- TERMINAL FLOATING WINDOW ---
                    if (showTerminalWindow) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFA151518)),
                            border = BorderStroke(1.dp, Color(0xFF2C2C35)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .size(width = 320.dp, height = 240.dp)
                                .align(Alignment.Center)
                                .clickable { /* Prevent drag consume */ }
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Title bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF232329))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Green, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("bash - terminal", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    IconButton(
                                        onClick = { showTerminalWindow = false },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Red, modifier = Modifier.size(12.dp))
                                    }
                                }

                                // Output text stream
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .background(Color.Black)
                                        .padding(8.dp)
                                ) {
                                    val scrollState = rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState)
                                    ) {
                                        terminalOutputLines.forEach { line ->
                                            Text(
                                                line,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (line.startsWith("Error")) Color.Red else Color.Green,
                                                fontSize = 11.sp
                                            )
                                        }

                                        // Input Line
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "user@vnc-desktop:~$ ",
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.Green,
                                                fontSize = 11.sp
                                            )
                                            BasicTextField(
                                                value = terminalInputText,
                                                onValueChange = { terminalInputText = it },
                                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                                keyboardActions = KeyboardActions(onSend = {
                                                    if (terminalInputText.isNotBlank()) {
                                                        val cmd = terminalInputText.trim()
                                                        terminalOutputLines.add("user@vnc-desktop:~$ $cmd")
                                                        when (cmd.lowercase()) {
                                                            "help" -> {
                                                                terminalOutputLines.add("Commands:")
                                                                terminalOutputLines.add("  help      Show help dialog")
                                                                terminalOutputLines.add("  sysinfo   Print specs")
                                                                terminalOutputLines.add("  ping      Ping diagnostic")
                                                                terminalOutputLines.add("  clear     Clear history")
                                                            }
                                                            "clear" -> {
                                                                terminalOutputLines.clear()
                                                            }
                                                            "sysinfo" -> {
                                                                terminalOutputLines.add("OS: Ubuntu Desktop LTS")
                                                                terminalOutputLines.add("Kernel: 5.15.0-generic")
                                                                terminalOutputLines.add("VNC Protocol: RFB 003.008")
                                                                terminalOutputLines.add("Visual mode: $colorDepth $quality quality")
                                                            }
                                                            "ping" -> {
                                                                terminalOutputLines.add("PING 192.168.1.1 (56 bytes data)")
                                                                terminalOutputLines.add("64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=1.24 ms")
                                                                terminalOutputLines.add("64 bytes from 192.168.1.1: icmp_seq=2 ttl=64 time=1.09 ms")
                                                                terminalOutputLines.add("--- ping stats --- 2 packets transmitted, 0% packet loss")
                                                            }
                                                            else -> {
                                                                terminalOutputLines.add("Error: Command not found: '$cmd'.")
                                                            }
                                                        }
                                                        terminalInputText = ""
                                                    }
                                                }),
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    color = Color.White,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- SYSTEM INFO WINDOW ---
                    if (showSystemInfoWindow) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFA151518)),
                            border = BorderStroke(1.dp, Color(0xFF2C2C35)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .size(width = 280.dp, height = 200.dp)
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Title bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF232329))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF00BCD4), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sistem Informasi", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    IconButton(
                                        onClick = { showSystemInfoWindow = false },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Red, modifier = Modifier.size(12.dp))
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF151518))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("IP Target: ${shortcut.url}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("VNC Protocol: RFB 003.008 (standard)", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("Simulated Host: Ubuntu 22.04 LTS", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("Warna: $colorDepth", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("Kualitas VNC: $quality", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text("Bandwidth Aktual: $bandwidthRate", color = Color(0xFF00BCD4), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Simulated Mouse Cursor Overlay
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(mouseX.roundToInt(), mouseY.roundToInt()) }
                            .size(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(16.dp)
                                .drawBehind {
                                    // Add shadow under mouse cursor
                                    drawCircle(Color(0x40000000), radius = 6.dp.toPx())
                                }
                        )
                    }

                    // --- BOTTOM SIMULATED TASKBAR ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(Color(0xE6212121))
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Start Menu Icon and quick launch apps
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF00BCD4), CircleShape)
                                    .clickable {
                                        showTerminalWindow = !showTerminalWindow
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            // Taskbar active tasks indicator
                            if (showTerminalWindow) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0x3300BCD4), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFF00BCD4), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Terminal", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }

                        // Right system clock
                        val timeString = remember {
                            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                            sdf.format(Date())
                        }
                        Text(
                            text = timeString,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // --- FLOATING PERFORMANCE STATS & CONTROL BAR ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Open/close stats trigger
                IconButton(
                    onClick = { showControlPanel = !showControlPanel },
                    modifier = Modifier
                        .background(Color(0xE6232329), RoundedCornerShape(10.dp))
                        .size(40.dp, 24.dp)
                ) {
                    Icon(
                        imageVector = if (showControlPanel) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Stats",
                        tint = Color.LightGray,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showControlPanel,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xE61E1E22)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF33333C)),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Diagnostics values
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column {
                                    Text("KUALITAS VNC", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text(quality.uppercase(), color = Color(0xFF00BCD4), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Column {
                                    Text("WARNA", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text(colorDepth, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("BANDWIDTH", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text(bandwidthRate, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("RATE", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    Text("$currentFps FPS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                // Interactive keyboard trigger
                                IconButton(
                                    onClick = {
                                        if (showTerminalWindow) {
                                            keyboardController?.show()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0x1A00BCD4), RoundedCornerShape(6.dp))
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Keyboard", tint = Color(0xFF00BCD4), modifier = Modifier.size(16.dp))
                                }

                                // Close session
                                IconButton(
                                    onClick = { onClose() },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(0x1AF44336), RoundedCornerShape(6.dp))
                                ) {
                                    Icon(Icons.Default.Home, contentDescription = "Back", tint = Color(0xFFF44336), modifier = Modifier.size(16.dp))
                                }
                            }

                            // Dynamic adjustment panel inside active session
                            Divider(color = Color(0xFF33333C), modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Ubah Kualitas:", color = Color.Gray, fontSize = 10.sp)
                                listOf("Low", "Medium", "High").forEach { q ->
                                    val active = quality == q
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (active) Color(0xFF00BCD4) else Color(0xFF151518),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                viewModel.updateActiveVncSettings(
                                                    quality = q,
                                                    colorDepth = colorDepth,
                                                    scale = scale
                                                )
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(q, color = if (active) Color.Black else Color.LightGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
