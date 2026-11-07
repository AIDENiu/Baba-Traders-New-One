package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.firebase.CloudSyncStatus
import com.example.data.model.UserEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.OwnerAlertNotification

@Composable
fun BabaLogoIcon(
    size: Dp = 42.dp,
    cornerRadius: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = Color.Black,
        shadowElevation = 2.dp
    ) {
        Image(
            painter = painterResource(id = R.drawable.baba_logo_img),
            contentDescription = "BABA Traders Logo",
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun BabaBrandTopBar(
    currentUser: UserEntity?,
    onSwitchUser: () -> Unit,
    alertNotification: OwnerAlertNotification? = null,
    onDismissAlert: () -> Unit = {},
    syncStatus: CloudSyncStatus = CloudSyncStatus.SYNCED,
    syncError: String? = null,
    onDismissSyncError: () -> Unit = {},
    isManualSyncing: Boolean = false,
    manualSyncStatus: String? = null,
    onSyncToOtherDevices: (() -> Unit)? = null
) {
    val effectiveSyncStatus = when {
        isManualSyncing || syncStatus == CloudSyncStatus.SYNCING -> CloudSyncStatus.SYNCING
        syncStatus == CloudSyncStatus.OFFLINE -> CloudSyncStatus.OFFLINE
        syncError != null || syncStatus == CloudSyncStatus.ERROR -> CloudSyncStatus.ERROR
        else -> CloudSyncStatus.SYNCED
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BabaRed)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand Logo & Title + Small Cloud Sync Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                BabaLogoIcon(size = 42.dp, cornerRadius = 10.dp)

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "BABA TRADERS.",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BabaYellow.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "v${com.example.BuildConfig.VERSION_NAME}",
                                color = BabaYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Small Top-Bar Cloud Sync Status: Synced / Syncing / Offline (will sync later) / Error
                        val statusDotColor = when (effectiveSyncStatus) {
                            CloudSyncStatus.SYNCED -> Color(0xFF4ADE80)
                            CloudSyncStatus.SYNCING -> BabaYellow
                            CloudSyncStatus.OFFLINE -> Color(0xFFFBBF24)
                            CloudSyncStatus.ERROR -> Color(0xFFFCA5A5)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.28f),
                            modifier = Modifier.testTag("top_bar_cloud_sync_status")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (effectiveSyncStatus == CloudSyncStatus.SYNCING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(8.dp),
                                        strokeWidth = 1.5.dp,
                                        color = statusDotColor
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(statusDotColor, CircleShape)
                                    )
                                }
                                Text(
                                    text = effectiveSyncStatus.label,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Manual "Sync Devices" Button in Top Bar (Owner Only)
                if (currentUser?.role == "owner" && onSyncToOtherDevices != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(enabled = !isManualSyncing) { onSyncToOtherDevices() }
                            .testTag("top_bar_sync_devices_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isManualSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = BabaRed
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Sync Devices",
                                    tint = BabaSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (isManualSyncing) "Syncing..." else "Sync Devices",
                                color = BabaDark,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }

                // User Profile / Switch Role Badge
                if (currentUser != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (currentUser.role == "owner") BabaYellow else Color.White.copy(alpha = 0.2f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onSwitchUser() }
                            .testTag("user_role_switch_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "User",
                                tint = if (currentUser.role == "owner") BabaDark else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Column {
                                Text(
                                    text = currentUser.name,
                                    color = if (currentUser.role == "owner") BabaDark else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = currentUser.role.uppercase(),
                                    color = if (currentUser.role == "owner") BabaDark.copy(alpha = 0.7f) else BabaYellow,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 8.5.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Switch",
                                tint = if (currentUser.role == "owner") BabaDark else Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // Red Banner Shown ONLY on Real Cloud Sync Failures
        if (syncError != null && effectiveSyncStatus != CloudSyncStatus.OFFLINE) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = BabaOverdueLight,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = syncError,
                        color = BabaOverdue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissSyncError,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Sync Error",
                            tint = BabaOverdue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Real-time Push / Alert Banner for Owner (when worker edits or deletes a job sheet)
        if (alertNotification != null && currentUser?.role == "owner") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = BabaYellow,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Alert",
                            tint = BabaDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = alertNotification.message,
                            color = BabaDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismissAlert,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = BabaDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
