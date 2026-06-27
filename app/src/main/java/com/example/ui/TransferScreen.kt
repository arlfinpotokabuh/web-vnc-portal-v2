package com.example.ui

import android.app.Application
import android.text.format.Formatter
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.TransferTask
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TransferScreen(
    transferViewModel: TransferViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(0) } // 0 = Active & History Transfers, 1 = Storage Files

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Upper Sub-tab switcher
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFFFF9800),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Transfer Manager", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Penyimpanan Internal", fontWeight = FontWeight.SemiBold) }
            )
        }

        AnimatedContent(
            targetState = selectedSubTab,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "SubTabTransition",
            modifier = Modifier.weight(1f)
        ) { tab ->
            when (tab) {
                0 -> TransferManagerTab(transferViewModel)
                1 -> StorageFilesTab(transferViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferManagerTab(viewModel: TransferViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val speeds by viewModel.speeds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (tasks.isEmpty()) {
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
                    text = "Belum ada transfer aktif atau riwayat.\nKlik tombol '+' untuk mulai mengunduh file.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tasks, key = { it.id }) { task ->
                    TransferTaskRow(
                        task = task,
                        speed = speeds[task.id] ?: "",
                        onPause = { viewModel.pauseTransfer(task.id) },
                        onResume = { viewModel.resumeTransfer(task.id) },
                        onDelete = { viewModel.deleteTransfer(task.id) }
                    )
                }
            }
        }

        // Floating Action Button to add download manually
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = Color(0xFFFF9800),
            contentColor = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .testTag("add_download_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tambah Unduhan Baru")
        }

        if (showAddDialog) {
            AddDownloadDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { url, name, isMultipart, parts ->
                    viewModel.addDownload(url, name, isMultipart, parts)
                    showAddDialog = false
                    Toast.makeText(context, "Unduhan dimulai!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun TransferTaskRow(
    task: TransferTask,
    speed: String,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2C2C35)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Name, Transfer Mode, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon indicator
                val iconBg = if (task.isDownload) Color(0x1AFFB74D) else Color(0x1A4DD0E1)
                val iconTint = if (task.isDownload) Color(0xFFFFB74D) else Color(0xFF00BCD4)
                val icon = if (task.isDownload) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Name and URL
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.name,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = task.url,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Actions: Pause/Resume/Delete
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (task.status == "PENDING" || task.status == "RUNNING") {
                        IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Jeda", tint = Color(0xFFFF9800), modifier = Modifier.size(18.dp))
                        }
                    } else if (task.status == "PAUSED" || task.status == "FAILED") {
                        IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Lanjutkan", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress state calculation
            val progress = if (task.totalBytes > 0) {
                task.transferredBytes.toFloat() / task.totalBytes.toFloat()
            } else {
                0f
            }
            val progressPct = (progress * 100).toInt()

            val transferredText = Formatter.formatShortFileSize(context, task.transferredBytes)
            val totalText = if (task.totalBytes > 0) {
                Formatter.formatShortFileSize(context, task.totalBytes)
            } else {
                "Unknown"
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { if (task.status == "COMPLETED") 1f else progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (task.isDownload) Color(0xFFFF9800) else Color(0xFF00BCD4),
                trackColor = Color(0xFF151518)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Stats footer: Speed, size, error info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Bytes & Parts description
                Text(
                    text = "$transferredText / $totalText" + (if (task.isMultipart) " (Multi ${task.numParts}P)" else ""),
                    color = Color.LightGray,
                    fontSize = 11.sp
                )

                // Right: Speed and Status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (task.status == "RUNNING" && speed.isNotEmpty()) {
                        Text(
                            text = speed,
                            color = if (task.isDownload) Color(0xFFFF9800) else Color(0xFF00BCD4),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    } else {
                        val statusColor = when (task.status) {
                            "COMPLETED" -> Color(0xFF4CAF50)
                            "PAUSED" -> Color(0xFFFFB74D)
                            "FAILED" -> Color(0xFFEF5350)
                            else -> Color.Gray
                        }
                        Text(
                            text = task.status,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Error Message (if any)
            if (!task.errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Error: ${task.errorMessage}",
                    color = Color(0xFFEF5350),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StorageFilesTab(viewModel: TransferViewModel) {
    val downloadedFiles by viewModel.downloadedFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var showUploadDialogForFile by remember { mutableStateOf<File?>(null) }
    var fileToPreview by remember { mutableStateOf<File?>(null) }

    // Storage capacity calculation
    val dir = viewModel.getStorageDir()
    val totalSpace = dir.totalSpace
    val freeSpace = dir.freeSpace
    val usedSpace = totalSpace - freeSpace
    val appUsedSpace = downloadedFiles.sumOf { it.length() }

    val formattedAppUsed = Formatter.formatShortFileSize(context, appUsedSpace)
    val formattedFree = Formatter.formatShortFileSize(context, freeSpace)

    Column(modifier = Modifier.fillMaxSize()) {
        // Storage Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2C2C35)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Kapasitas Penyimpanan Sandboxed",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (usedSpace.toFloat() / totalSpace.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color(0xFF151518)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Digunakan Aplikasi: $formattedAppUsed",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Sisa Ruang Bebas: $formattedFree",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "File yang Diunduh",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
            IconButton(onClick = { viewModel.refreshFilesList() }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Segarkan", tint = Color.LightGray)
            }
        }

        if (downloadedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada file di penyimpanan internal.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(downloadedFiles) { file ->
                    StorageFileRow(
                        file = file,
                        onOpen = { fileToPreview = file },
                        onUpload = { showUploadDialogForFile = file },
                        onDelete = { viewModel.deleteLocalFile(file) }
                    )
                }
            }
        }

        if (showUploadDialogForFile != null) {
            AddUploadDialog(
                file = showUploadDialogForFile!!,
                onDismiss = { showUploadDialogForFile = null },
                onAdd = { url, isMultipart, parts ->
                    viewModel.addUpload(url, showUploadDialogForFile!!, isMultipart, parts)
                    showUploadDialogForFile = null
                    Toast.makeText(context, "Unggahan multipart dimulai!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (fileToPreview != null) {
            LocalFilePreviewDialog(
                file = fileToPreview!!,
                onDismiss = { fileToPreview = null }
            )
        }
    }
}

@Composable
fun StorageFileRow(
    file: File,
    onOpen: () -> Unit,
    onUpload: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val sizeText = Formatter.formatShortFileSize(context, file.length())
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateText = sdf.format(Date(file.lastModified()))

    val ext = file.name.substringAfterLast(".", "").lowercase()
    val isTxt = ext in listOf("txt", "json", "xml", "html", "css", "js", "log", "md")

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151518)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File Type Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1E1E22), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val icon = if (isTxt) Icons.Default.Menu else Icons.Default.Star
                Icon(icon, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            // File Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$sizeText  •  $dateText",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }

            // Operation Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Open button (if previewable)
                if (isTxt) {
                    IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "Pratinjau", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                    }
                }

                // Upload button
                IconButton(onClick = onUpload, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Share, contentDescription = "Unggah", tint = Color(0xFF00BCD4), modifier = Modifier.size(16.dp))
                }

                // Delete button
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ---------------- DIALOGS ----------------

@Composable
fun AddDownloadDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, filename: String, isMultipart: Boolean, parts: Int) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var url by remember { mutableStateOf("") }
    var filename by remember { mutableStateOf("") }
    var isMultipart by remember { mutableStateOf(true) }
    var numParts by remember { mutableStateOf(4f) }

    // Auto-prefill URL from clipboard if valid
    LaunchedEffect(Unit) {
        val text = clipboardManager.getText()?.text
        if (text != null && (text.startsWith("http://") || text.startsWith("https://"))) {
            url = text
            filename = URLUtil.guessFileName(text, null, null) ?: "file_download"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2C2C35)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Unduhan Multipart Baru",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // URL Input
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        if (it.isNotBlank()) {
                            filename = URLUtil.guessFileName(it, null, null) ?: "file_download"
                        }
                    },
                    label = { Text("Tautan URL Unduhan") },
                    placeholder = { Text("https://example.com/file.zip") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151518),
                        unfocusedContainerColor = Color(0xFF151518),
                        focusedBorderColor = Color(0xFFFF9800),
                        unfocusedBorderColor = Color(0xFF33333C),
                        focusedLabelColor = Color(0xFFFF9800)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("download_dialog_url_input"),
                    singleLine = true
                )

                // Filename Input
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text("Nama File") },
                    placeholder = { Text("file.zip") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151518),
                        unfocusedContainerColor = Color(0xFF151518),
                        focusedBorderColor = Color(0xFFFF9800),
                        unfocusedBorderColor = Color(0xFF33333C),
                        focusedLabelColor = Color(0xFFFF9800)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("download_dialog_name_input"),
                    singleLine = true
                )

                // Multipart Mode Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Unduhan Multipart (Multi-part)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Bagi file ke beberapa bagian untuk mempercepat", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isMultipart,
                        onCheckedChange = { isMultipart = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFF9800),
                            checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.5f)
                        )
                    )
                }

                // Chunk Parts Slider
                if (isMultipart) {
                    Text(
                        text = "Jumlah Koneksi/Bagian: ${numParts.toInt()}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Slider(
                        value = numParts,
                        onValueChange = { numParts = it },
                        valueRange = 2f..10f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF9800),
                            activeTrackColor = Color(0xFFFF9800)
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (url.isNotBlank() && filename.isNotBlank()) {
                                onAdd(url, filename, isMultipart, numParts.toInt())
                            } else {
                                Toast.makeText(context, "URL dan Nama File wajib diisi!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("download_dialog_submit")
                    ) {
                        Text("Unduh Sekarang", color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun AddUploadDialog(
    file: File,
    onDismiss: () -> Unit,
    onAdd: (url: String, isMultipart: Boolean, parts: Int) -> Unit
) {
    var url by remember { mutableStateOf("https://httpbin.org/post") } // Good test fallback
    var isMultipart by remember { mutableStateOf(true) }
    var numParts by remember { mutableStateOf(4f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2C2C35)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Unggah File Baru",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "File: ${file.name}",
                    color = Color(0xFF00BCD4),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // URL Input
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Tautan URL Unggahan (HTTP POST)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF151518),
                        unfocusedContainerColor = Color(0xFF151518),
                        focusedBorderColor = Color(0xFF00BCD4),
                        unfocusedBorderColor = Color(0xFF33333C),
                        focusedLabelColor = Color(0xFF00BCD4)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    singleLine = true
                )

                // Chunked Upload Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Unggahan Chunked Multipart", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Bagi file dan unggah perbagian (bisa jeda/lanjut)", color = Color.Gray, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isMultipart,
                        onCheckedChange = { isMultipart = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF00BCD4),
                            checkedTrackColor = Color(0xFF00BCD4).copy(alpha = 0.5f)
                        )
                    )
                }

                // Chunk Parts Slider
                if (isMultipart) {
                    Text(
                        text = "Jumlah Chunk: ${numParts.toInt()}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Slider(
                        value = numParts,
                        onValueChange = { numParts = it },
                        valueRange = 2f..10f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00BCD4),
                            activeTrackColor = Color(0xFF00BCD4)
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (url.isNotBlank()) {
                                onAdd(url, isMultipart, numParts.toInt())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Mulai Unggah", color = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun LocalFilePreviewDialog(
    file: File,
    onDismiss: () -> Unit
) {
    var content by remember { mutableStateOf("Memuat isi file...") }

    LaunchedEffect(file) {
        try {
            val text = file.readText()
            content = if (text.length > 5000) {
                text.substring(0, 5000) + "\n\n--- [Konten dipotong, terlalu panjang] ---"
            } else {
                text
            }
        } catch (e: Exception) {
            content = "Gagal memuat isi file: ${e.localizedMessage}"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E22)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2C2C35)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Pratinjau: ${file.name}",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // Preview Box
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 300.dp)
                        .fillMaxWidth()
                        .background(Color(0xFF151518), RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = content,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C35)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Tutup", color = Color.White)
                }
            }
        }
    }
}
