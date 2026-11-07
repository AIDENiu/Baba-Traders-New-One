package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.backup.BackupFileInfo
import com.example.data.backup.BackupManager
import com.example.ui.theme.*
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppControlScreen(
    customersCount: Int,
    machinesCount: Int,
    jobSheetsCount: Int,
    paymentsCount: Int,
    expensesCount: Int,
    activityLogsCount: Int,
    backupsList: List<BackupFileInfo>,
    isBusy: Boolean,
    statusMessage: String?,
    onClearStatus: () -> Unit,
    onRefreshBackups: () -> Unit,
    onCreateFullBackupZip: (onSuccess: (File) -> Unit, onError: (String) -> Unit) -> Unit,
    onSaveBackupZipToUri: (sourceZip: File, destUri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onRestoreFromSavedZip: (zipFile: File, onSuccess: (Int) -> Unit, onError: (String) -> Unit) -> Unit,
    onRestoreFromExternalUri: (uri: Uri, onSuccess: (Int) -> Unit, onError: (String) -> Unit) -> Unit,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        onRefreshBackups()
    }

    var filePendingSaveToDevice by remember { mutableStateOf<File?>(null) }
    var backupPendingRestoreConfirm by remember { mutableStateOf<BackupFileInfo?>(null) }
    var uriPendingRestoreConfirm by remember { mutableStateOf<Uri?>(null) }

    // System "Save As" document creator for exporting a Backup ZIP to Downloads / Device Storage
    val saveZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri: Uri? ->
        val srcFile = filePendingSaveToDevice
        if (destUri != null && srcFile != null) {
            onSaveBackupZipToUri(
                srcFile,
                destUri,
                {
                    Toast.makeText(context, "Backup ZIP saved to device storage!", Toast.LENGTH_LONG).show()
                },
                { err ->
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }
        filePendingSaveToDevice = null
    }

    // System file picker for selecting any Backup ZIP file from device storage to restore
    val openRestoreZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri: Uri? ->
        if (pickedUri != null) {
            uriPendingRestoreConfirm = pickedUri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "App Control — Backup & Restore",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Total App Data & Photos ZIP Management",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("app_control_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BabaRed)
            )
        },
        containerColor = BabaBg
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Banner
            if (!statusMessage.isNullOrBlank()) {
                item {
                    val isError = statusMessage.contains("Error", ignoreCase = true) ||
                        statusMessage.contains("failed", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isError) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                        border = BorderStroke(
                            1.dp,
                            if (isError) BabaOverdue.copy(alpha = 0.4f) else BabaSuccess.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = BabaSuccess
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                        contentDescription = "Status",
                                        tint = if (isError) BabaOverdue else BabaSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = statusMessage,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isError) BabaOverdue else Color(0xFF1B5E20)
                                )
                            }
                            if (!isBusy) {
                                IconButton(
                                    onClick = onClearStatus,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = BabaGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. Current Device Data Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(BabaRedLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = "Total Data",
                                    tint = BabaRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Total App Data on This Device",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark
                                )
                                Text(
                                    text = "All records below + Customer/Machine/ID photos are included in the ZIP",
                                    fontSize = 11.sp,
                                    color = BabaGray
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DataCountChip("Job Sheets", jobSheetsCount, Modifier.weight(1f))
                            DataCountChip("Customers", customersCount, Modifier.weight(1f))
                            DataCountChip("Machines", machinesCount, Modifier.weight(1f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DataCountChip("Payments", paymentsCount, Modifier.weight(1f))
                            DataCountChip("Expenses", expensesCount, Modifier.weight(1f))
                            DataCountChip("Audit Logs", activityLogsCount, Modifier.weight(1f))
                        }
                    }
                }
            }

            // 2. Backup Total App Data in ZIP Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BabaSuccess.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFE8F5E9), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = "Backup ZIP",
                                    tint = BabaSuccess,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "1. Backup Total App Data (ZIP)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BabaDark
                                )
                                Text(
                                    text = "Exports all Job Sheets, Customers, Machines, Payments, Expenses, Cash Reports, CSV & Local Photos into a single .zip file.",
                                    fontSize = 12.sp,
                                    color = BabaGray
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onCreateFullBackupZip(
                                    { zipFile ->
                                        Toast.makeText(
                                            context,
                                            "Full Backup Created: ${zipFile.name}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        BackupManager.shareBackupZip(context, zipFile)
                                    },
                                    { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = BabaSuccess),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("backup_total_data_zip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = "Backup Now",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBusy) "Processing..." else "Backup Total App Data in ZIP & Share",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onCreateFullBackupZip(
                                    { zipFile ->
                                        filePendingSaveToDevice = zipFile
                                        saveZipLauncher.launch(zipFile.name)
                                    },
                                    { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BabaSuccess),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_backup_zip_to_device_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save to Downloads",
                                tint = BabaSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create & Save ZIP to Device / Downloads",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = BabaSuccess
                            )
                        }
                    }
                }
            }

            // 3. Restore Total App Data from ZIP Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BabaRed.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(BabaRedLight, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SettingsBackupRestore,
                                    contentDescription = "Restore ZIP",
                                    tint = BabaRed,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "2. Restore App Data from ZIP",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BabaDark
                                )
                                Text(
                                    text = "Pick any BABA_Backup_*.zip file from your phone or tablet storage to restore all records and photos non-destructively.",
                                    fontSize = 12.sp,
                                    color = BabaGray
                                )
                            }
                        }

                        Button(
                            onClick = {
                                openRestoreZipLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("restore_from_file_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Select Backup ZIP",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select Backup ZIP File to Restore",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Text(
                            text = "Safe & Non-Destructive: Restoring a backup never deletes or overwrites your existing Job Sheets or Payments. It merges missing records & photos and syncs them to the Cloud.",
                            fontSize = 11.sp,
                            color = BabaGray
                        )
                    }
                }
            }

            // 4. Saved Backups on Device Section
            item {
                Text(
                    text = "Saved Backups on This Device (${backupsList.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BabaDark,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (backupsList.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "No Backups Yet",
                                tint = BabaGray,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No local backup ZIPs created yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BabaDark
                            )
                            Text(
                                text = "Tap 'Backup Total App Data in ZIP' above to create your first full backup.",
                                fontSize = 12.sp,
                                color = BabaGray
                            )
                        }
                    }
                }
            } else {
                items(backupsList, key = { it.fileName }) { backupInfo ->
                    SavedBackupItemCard(
                        backupInfo = backupInfo,
                        isBusy = isBusy,
                        onRestoreClick = { backupPendingRestoreConfirm = backupInfo },
                        onSaveToDeviceClick = {
                            filePendingSaveToDevice = backupInfo.file
                            saveZipLauncher.launch(backupInfo.fileName)
                        },
                        onShareClick = {
                            BackupManager.shareBackupZip(context, backupInfo.file)
                        }
                    )
                }
            }
        }
    }

    // Confirmation Dialog for Restoring from a Saved Local Backup ZIP
    if (backupPendingRestoreConfirm != null) {
        val target = backupPendingRestoreConfirm!!
        AlertDialog(
            onDismissRequest = { backupPendingRestoreConfirm = null },
            title = {
                Text("Restore Backup ZIP?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Restore all missing records and local photos from '${target.fileName}' (${target.formattedDate})?\n\nExisting data on this device will be kept safely."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        backupPendingRestoreConfirm = null
                        onRestoreFromSavedZip(
                            target.file,
                            { count ->
                                Toast.makeText(
                                    context,
                                    "Restore complete! $count new record(s) & photos restored.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                    modifier = Modifier.testTag("confirm_restore_saved_zip_button")
                ) {
                    Text("Restore Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { backupPendingRestoreConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation Dialog for Restoring from an External Picked ZIP File Uri
    if (uriPendingRestoreConfirm != null) {
        val targetUri = uriPendingRestoreConfirm!!
        AlertDialog(
            onDismissRequest = { uriPendingRestoreConfirm = null },
            title = {
                Text("Restore Selected ZIP File?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Restore all Customers, Machines, Job Sheets, Payments, Expenses, Activity Logs, and Photos from the selected backup ZIP file?\n\nExisting records on this device will not be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        uriPendingRestoreConfirm = null
                        onRestoreFromExternalUri(
                            targetUri,
                            { count ->
                                Toast.makeText(
                                    context,
                                    "Restore complete! $count new record(s) & photos restored.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                    modifier = Modifier.testTag("confirm_restore_external_zip_button")
                ) {
                    Text("Restore Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { uriPendingRestoreConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DataCountChip(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = BabaBg,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = BabaDark
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = BabaGray,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SavedBackupItemCard(
    backupInfo: BackupFileInfo,
    isBusy: Boolean,
    onRestoreClick: () -> Unit,
    onSaveToDeviceClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val sizeFormatted = remember(backupInfo.sizeBytes) {
        val kb = backupInfo.sizeBytes / 1024.0
        if (kb >= 1024.0) {
            String.format(Locale.US, "%.2f MB", kb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f KB", maxOf(1.0, kb))
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(BabaYellow.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = "Backup ZIP",
                        tint = BabaYellowDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = backupInfo.fileName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                    Text(
                        text = "${backupInfo.formattedDate} • $sizeFormatted",
                        fontSize = 11.sp,
                        color = BabaGray
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRestoreClick,
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onSaveToDeviceClick,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save ZIP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onShareClick,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
