package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.UserEntity
import com.example.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Dialog to register or update the Owner's mobile number.
 * Kept exclusively in Manage Staff for the Owner.
 */
@Composable
fun UpdateOwnerMobileDialog(
    owner: UserEntity,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var phoneInput by remember { mutableStateOf(owner.registeredPhone.ifBlank { "9876543210" }) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(BabaRedLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = BabaRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Register Owner Mobile",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = BabaDark
                        )
                        Text(
                            text = "Owner: ${owner.name} (Shop Owner)",
                            fontSize = 12.sp,
                            color = BabaGray
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BabaYellowLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BabaYellowDark.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = BabaDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "This mobile number is stored exclusively for the Owner. Security OTPs for Owner PIN change and sensitive operations will be sent here.",
                            fontSize = 11.sp,
                            color = BabaDark,
                            lineHeight = 15.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        if (digits.length <= 10) {
                            phoneInput = digits
                            errorMessage = null
                        }
                    },
                    label = { Text("Owner Mobile Number *") },
                    prefix = { Text("+91 ", fontWeight = FontWeight.Bold, color = BabaDark) },
                    placeholder = { Text("10-digit mobile number") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = BabaRed)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = babaTextFieldColors(),
                    textStyle = babaInputTextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("owner_mobile_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = BabaOverdue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val clean = phoneInput.trim()
                            if (clean.length != 10) {
                                errorMessage = "Please enter a valid 10-digit mobile number"
                            } else {
                                onSave(clean)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Mobile", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Multi-step dialog to verify Owner's registered mobile number via OTP
 * and then allow setting a new 4-digit PIN.
 */
@Composable
fun OwnerPinChangeWithOtpDialog(
    owner: UserEntity,
    onDismiss: () -> Unit,
    onPinChanged: (newPin: String) -> Unit
) {
    // Current step: 1 = OTP Verification, 2 = Set New PIN
    var currentStep by remember { mutableIntStateOf(1) }

    // OTP states
    var generatedOtp by remember {
        mutableStateOf(String.format("%06d", (100000..999999).random()))
    }
    var enteredOtp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var resendCooldown by remember { mutableIntStateOf(30) }
    var showSmsBanner by remember { mutableStateOf(true) }

    // PIN states
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    // Resend countdown timer
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000L)
            resendCooldown -= 1
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(if (currentStep == 1) BabaRedLight else BabaSuccessLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (currentStep == 1) Icons.Default.Lock else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (currentStep == 1) BabaRed else BabaSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (currentStep == 1) "Owner OTP Verification" else "Set New Owner PIN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = BabaDark
                        )
                        Text(
                            text = if (currentStep == 1) "Step 1 of 2: Verify registered phone" else "Step 2 of 2: Choose 4-digit PIN",
                            fontSize = 12.sp,
                            color = BabaGray
                        )
                    }
                }

                if (currentStep == 1) {
                    // STEP 1: OTP VERIFICATION
                    val targetPhone = owner.registeredPhone.ifBlank { "9876543210" }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BabaLightGray,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = BabaRed)
                            Column {
                                Text("Registered Mobile Number:", fontSize = 11.sp, color = BabaGray)
                                Text(
                                    text = "+91 $targetPhone",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BabaDark
                                )
                            }
                        }
                    }

                    // Simulated Real-Time Incoming SMS Notification Banner
                    if (showSmsBanner) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Sms,
                                            contentDescription = null,
                                            tint = BabaYellow,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "SIMULATED SMS • BABA-SEC",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaYellow
                                        )
                                    }
                                    Text(
                                        text = "Now",
                                        fontSize = 10.sp,
                                        color = Color.LightGray
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Your OTP for Owner PIN change is $generatedOtp. Valid for 5 minutes. Do not share.",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BabaYellow,
                                        modifier = Modifier.clickable {
                                            enteredOtp = generatedOtp
                                            otpError = null
                                        }
                                    ) {
                                        Text(
                                            text = "Auto-fill OTP: $generatedOtp",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BabaDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // OTP Input
                    OutlinedTextField(
                        value = enteredOtp,
                        onValueChange = {
                            if (it.length <= 6) {
                                enteredOtp = it.filter { ch -> ch.isDigit() }
                                otpError = null
                            }
                        },
                        label = { Text("Enter 6-Digit OTP") },
                        placeholder = { Text("••••••") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = BabaRed)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ).copy(textAlign = TextAlign.Center),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_input_field"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (otpError != null) {
                        Text(
                            text = otpError!!,
                            color = BabaOverdue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Resend OTP row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (resendCooldown > 0) {
                            Text(
                                text = "Resend OTP in ${resendCooldown}s",
                                fontSize = 11.sp,
                                color = BabaGray
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    generatedOtp = String.format("%06d", (100000..999999).random())
                                    resendCooldown = 30
                                    showSmsBanner = true
                                    enteredOtp = ""
                                    otpError = null
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Resend OTP", fontSize = 12.sp, color = BabaRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (enteredOtp.length != 6) {
                                    otpError = "Please enter the full 6-digit OTP"
                                } else if (enteredOtp != generatedOtp) {
                                    otpError = "Invalid OTP code. Please check SMS and try again."
                                } else {
                                    otpError = null
                                    currentStep = 2 // Move to Set New PIN
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Verify OTP", color = Color.White)
                        }
                    }
                } else {
                    // STEP 2: SET NEW PIN
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BabaSuccessLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaSuccess.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BabaSuccess, modifier = Modifier.size(18.dp))
                            Text(
                                text = "OTP verified. Now enter a new 4-digit security PIN for Owner.",
                                fontSize = 12.sp,
                                color = BabaDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            val digits = it.filter { ch -> ch.isDigit() }
                            if (digits.length <= 4) {
                                newPin = digits
                                pinError = null
                            }
                        },
                        label = { Text("New 4-Digit Owner PIN *") },
                        placeholder = { Text("e.g. 1234") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = BabaRed)
                        },
                        trailingIcon = {
                            IconButton(onClick = { pinVisible = !pinVisible }) {
                                Icon(
                                    imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = BabaGray
                                )
                            }
                        },
                        visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_owner_pin_field"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = {
                            val digits = it.filter { ch -> ch.isDigit() }
                            if (digits.length <= 4) {
                                confirmPin = digits
                                pinError = null
                            }
                        },
                        label = { Text("Confirm New 4-Digit PIN *") },
                        placeholder = { Text("Re-enter PIN") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = BabaRed)
                        },
                        visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = babaTextFieldColors(),
                        textStyle = babaInputTextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_owner_pin_field"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (pinError != null) {
                        Text(
                            text = pinError!!,
                            color = BabaOverdue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                if (newPin.length != 4) {
                                    pinError = "PIN must be exactly 4 numeric digits"
                                } else if (newPin != confirmPin) {
                                    pinError = "PINs do not match. Please verify."
                                } else {
                                    onPinChanged(newPin)
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BabaRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Confirm & Save", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
