package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.OwnerAlertNotification

@Composable
fun BabaBrandTopBar(
    currentUser: UserEntity?,
    onSwitchUser: () -> Unit,
    alertNotification: OwnerAlertNotification? = null,
    onDismissAlert: () -> Unit = {}
) {
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
            // Brand Logo & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Stylized BABA icon mark
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(BabaRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "B",
                            color = BabaYellow,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                    }
                }

                Column {
                    Text(
                        text = "BABA TRADERS",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "TOOLS FOR A STRONGER TOMORROW",
                        color = BabaYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    )
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User",
                            tint = if (currentUser.role == "owner") BabaDark else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = currentUser.name,
                                color = if (currentUser.role == "owner") BabaDark else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = currentUser.role.uppercase(),
                                color = if (currentUser.role == "owner") BabaDark.copy(alpha = 0.7f) else BabaYellow,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch",
                            tint = if (currentUser.role == "owner") BabaDark else Color.White,
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
