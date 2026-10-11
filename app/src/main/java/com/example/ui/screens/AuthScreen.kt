package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.model.UserEntity
import com.example.ui.theme.*
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    users: List<UserEntity>,
    onLoginSuccess: (UserEntity) -> Unit
) {
    val defaultOperators = remember {
        listOf(
            UserEntity(
                id = "user_owner_naveen",
                name = "Naveen",
                phoneOrUsername = "naveen",
                pin = "1234",
                role = "owner",
                isActive = true,
                maxDiscountPercent = 15.0,
                maxDiscountAmount = 200.0
            ),
            UserEntity(
                id = "user_worker_suresh",
                name = "Suresh",
                phoneOrUsername = "suresh",
                pin = "0000",
                role = "worker",
                isActive = true
            )
        )
    }

    val activeUsers = remember(users) {
        val list = users.filter { it.isActive }
        if (list.isNotEmpty()) list else defaultOperators
    }

    var selectedUser by remember(activeUsers) {
        mutableStateOf(activeUsers.firstOrNull { it.role == "owner" } ?: activeUsers.firstOrNull())
    }
    var pin by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockoutUntil by remember { mutableLongStateOf(0L) }
    var isGoogleSigningIn by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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
                        activeUsers.forEach { user ->
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
                            if (System.currentTimeMillis() < lockoutUntil) {
                                val remainingSec = ((lockoutUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
                                errorMessage = "Lockout active due to repeated failures. Please wait ${remainingSec}s."
                                return@Button
                            }
                            if (user.pin.isNotEmpty() && pin != user.pin) {
                                failedAttempts++
                                if (failedAttempts >= 5) {
                                    lockoutUntil = System.currentTimeMillis() + 60_000L
                                    errorMessage = "5 failed attempts. Account locked out for 60 seconds."
                                } else {
                                    errorMessage = "Incorrect PIN (${5 - failedAttempts} attempts remaining). Default is ${if (user.role == "owner") "1234" else "0000"}"
                                }
                            } else {
                                failedAttempts = 0
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

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BabaBorder)
                        Text(
                            text = " OR CLOUD SYNC ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BabaGray,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = BabaBorder)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Interactive Google Sign-In via Credential Manager
                    OutlinedButton(
                        onClick = {
                            val user = selectedUser ?: return@OutlinedButton
                            isGoogleSigningIn = true
                            errorMessage = null
                            val clientId = try {
                                context.getString(R.string.default_web_client_id)
                            } catch (e: Exception) {
                                isGoogleSigningIn = false
                                errorMessage = "Google Sign-In configuration missing"
                                return@OutlinedButton
                            }

                            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
                            val credentialManager = CredentialManager.create(context)

                            coroutineScope.launch {
                                try {
                                    val result = credentialManager.getCredential(context as Activity, request)
                                    val credential = result.credential
                                    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                                        val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                                        val firebaseUser = authResult.user
                                        if (firebaseUser != null) {
                                            val authenticatedUser = user.copy(id = firebaseUser.uid)
                                            isGoogleSigningIn = false
                                            onLoginSuccess(authenticatedUser)
                                        } else {
                                            isGoogleSigningIn = false
                                            errorMessage = "Firebase user session empty"
                                        }
                                    } else {
                                        isGoogleSigningIn = false
                                        errorMessage = "Unexpected credential returned"
                                    }
                                } catch (e: GetCredentialCancellationException) {
                                    Log.w("Auth", "Google Sign-In cancelled: ${e.message}", e)
                                    isGoogleSigningIn = false
                                } catch (e: Exception) {
                                    Log.e("Auth", "Google Sign-In failed", e)
                                    isGoogleSigningIn = false
                                    errorMessage = e.localizedMessage ?: "Google Sign-In failed"
                                }
                            }
                        },
                        enabled = !isGoogleSigningIn,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_signin_button"),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BabaBorder)
                    ) {
                        if (isGoogleSigningIn) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BabaRed, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connecting to Firebase...", fontSize = 13.sp, color = BabaDark)
                        } else {
                            Icon(
                                imageVector = Icons.Default.SupervisorAccount,
                                contentDescription = null,
                                tint = BabaRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sign in with Google (${selectedUser?.name ?: "Staff"})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = BabaDark
                            )
                        }
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
