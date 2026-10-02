package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserEntity
import com.example.ui.theme.*

@Composable
fun AuthScreen(
    users: List<UserEntity>,
    onLoginSuccess: (UserEntity) -> Unit
) {
    var selectedUser by remember(users) {
        mutableStateOf(users.firstOrNull { it.role == "owner" } ?: users.firstOrNull())
    }
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BabaBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Brand Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BabaRed)
                    .padding(vertical = 36.dp, horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Stylized Emblem
                    Surface(
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        tonalElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "B",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                color = BabaRed
                            )
                        }
                    }

                    Text(
                        text = "BABA TRADERS",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "TOOLS FOR A STRONGER TOMORROW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaYellow,
                        letterSpacing = 1.2.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BabaYellow.copy(alpha = 0.2f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "JOB SHEET & MACHINERY RENTALS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Login Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select Shop Operator",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BabaDark
                    )
                    Text(
                        text = "Choose your role to access job sheets",
                        fontSize = 12.sp,
                        color = BabaGray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // User selector chips / cards
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        users.filter { it.isActive }.forEach { user ->
                            val isSelected = selectedUser?.id == user.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) BabaRed else BabaBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedUser = user
                                        errorMessage = null
                                        pin = ""
                                    }
                                    .testTag("user_select_${user.role}"),
                                color = if (isSelected) BabaRedLight else Color.White
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(
                                                    if (user.role == "owner") BabaYellow else BabaLightGray,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (user.role == "owner") Icons.Default.SupervisorAccount else Icons.Default.Engineering,
                                                contentDescription = user.role,
                                                tint = if (user.role == "owner") BabaDark else BabaRed,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = user.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = BabaDark
                                            )
                                            Text(
                                                text = if (user.role == "owner") "Shop Owner (Full Access)" else "Shop Worker (Job Sheets Only)",
                                                fontSize = 11.sp,
                                                color = BabaGray
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (user.role == "owner") BabaYellow else BabaLightGray
                                    ) {
                                        Text(
                                            text = user.role.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = BabaDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // PIN Input Field
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 6) {
                                pin = it
                                errorMessage = null
                            }
                        },
                        label = { Text("Enter 4-Digit Security PIN") },
                        placeholder = { Text(if (selectedUser?.role == "owner") "PIN: 1234" else "PIN: 0000") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = "PIN", tint = BabaRed)
                        },
                        trailingIcon = {
                            IconButton(onClick = { pinVisible = !pinVisible }) {
                                Icon(
                                    imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle PIN",
                                    tint = BabaGray
                                )
                            }
                        },
                        textStyle = babaInputTextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        singleLine = true,
                        colors = babaTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_input_field"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = BabaOverdue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Login Button
                    Button(
                        onClick = {
                            val user = selectedUser
                            if (user == null) {
                                errorMessage = "Please select an operator"
                                return@Button
                            }
                            if (user.pin.isNotEmpty() && pin != user.pin) {
                                errorMessage = "Incorrect PIN. Default is ${if (user.role == "owner") "1234" else "0000"}"
                            } else {
                                onLoginSuccess(user)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_submit_button")
                    ) {
                        Text(
                            text = "Login to BABA Job Sheet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "BABA Traders & Engineering Hardwares",
                fontSize = 11.sp,
                color = BabaGray
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
