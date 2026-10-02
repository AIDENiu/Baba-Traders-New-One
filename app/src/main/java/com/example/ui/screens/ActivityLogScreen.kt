package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ActivityLogEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    logs: List<ActivityLogEntity>,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }
    var selectedActionFilter by remember { mutableStateOf<String?>(null) }
    var selectedLogForDetail by remember { mutableStateOf<ActivityLogEntity?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()) }

    val filteredLogs = remember(logs, selectedActionFilter) {
        if (selectedActionFilter == null) logs else logs.filter { it.action == selectedActionFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Audit Log", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BabaRed)
            )
        },
        containerColor = BabaBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Action Filter Chips (Horizontally scrollable to avoid wrapping and vertical expansion)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedActionFilter == null,
                    onClick = { selectedActionFilter = null },
                    label = { Text("All (${logs.size})", fontSize = 11.sp, maxLines = 1) }
                )
                listOf("deleted" to "Deletions", "edited" to "Edits", "created" to "New Jobs", "returned" to "Returns").forEach { (action, label) ->
                    val count = logs.count { it.action == action }
                    FilterChip(
                        selected = selectedActionFilter == action,
                        onClick = { selectedActionFilter = action },
                        label = { Text("$label ($count)", fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "No activity logs",
                            tint = BabaGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No activity log entries found",
                            color = BabaGray,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val actionColor = when (log.action) {
                            "deleted" -> BabaOverdue
                            "edited" -> BabaYellowDark
                            "returned" -> BabaSuccess
                            else -> BabaRed
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedLogForDetail = log }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(actionColor.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (log.action) {
                                            "deleted" -> Icons.Default.Delete
                                            "edited" -> Icons.Default.Edit
                                            "returned" -> Icons.Default.Check
                                            else -> Icons.Default.Add
                                        },
                                        contentDescription = log.action,
                                        tint = actionColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.performedByName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = BabaDark
                                        )
                                        Surface(shape = RoundedCornerShape(6.dp), color = actionColor.copy(alpha = 0.15f)) {
                                            Text(
                                                text = log.action.uppercase(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = actionColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = log.changeSummary,
                                        fontSize = 12.sp,
                                        color = BabaDark,
                                        lineHeight = 16.sp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = dateFormat.format(Date(log.timestamp)),
                                        fontSize = 10.sp,
                                        color = BabaGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    if (selectedLogForDetail != null) {
        val log = selectedLogForDetail!!
        Dialog(onDismissRequest = { selectedLogForDetail = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Activity Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BabaDark)
                    HorizontalDivider(color = BabaBorder)

                    Text("Staff Member: ${log.performedByName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BabaDark)
                    Text("Action Type: ${log.action.uppercase()}", fontSize = 12.sp, color = BabaRed)
                    Text("Associated Job: ${log.jobSheetId}", fontSize = 12.sp, color = BabaGray)
                    Text("Timestamp: ${dateFormat.format(Date(log.timestamp))}", fontSize = 12.sp, color = BabaGray)

                    Surface(shape = RoundedCornerShape(8.dp), color = BabaLightGray, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = log.changeSummary,
                            fontSize = 13.sp,
                            color = BabaDark,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Button(
                        onClick = { selectedLogForDetail = null },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaDark),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
