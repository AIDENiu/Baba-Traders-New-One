package com.example.ui.theme

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BabaRed = Color(0xFFE10600)
val BabaRedDark = Color(0xFFB30500)
val BabaRedLight = Color(0xFFFFECEB)
val BabaYellow = Color(0xFFFFD200)
val BabaYellowDark = Color(0xFFE5BC00)
val BabaYellowLight = Color(0xFFFFF9DB)
val BabaDark = Color(0xFF1A1A1A)
val BabaGray = Color(0xFF6B7280)
val BabaLightGray = Color(0xFFF3F4F6)
val BabaBg = Color(0xFFF8F8F8)
val BabaSurface = Color(0xFFFFFFFF)
val BabaSuccess = Color(0xFF00875A)
val BabaSuccessLight = Color(0xFFE3FCEF)
val BabaOverdue = Color(0xFFD32F2F)
val BabaOverdueLight = Color(0xFFFFEBEE)
val BabaBorder = Color(0xFFBDBDBD)

// Legacy alias compatibility
val IndustrialAmber = Color(0xFFD97706)
val IndustrialAmberLight = Color(0xFFF59E0B)
val IndustrialAmberDark = Color(0xFFB45309)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)
val AccentGreen = Color(0xFF10B981)
val AccentRed = Color(0xFFEF4444)
val AccentBlue = Color(0xFF3B82F6)

fun babaInputTextStyle(
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    fontWeight: FontWeight = FontWeight.Normal
) = TextStyle(
    color = BabaDark,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = FontFamily.Default
)

@Composable
fun babaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = BabaDark,
    unfocusedTextColor = BabaDark,
    disabledTextColor = BabaDark,
    errorTextColor = BabaOverdue,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = BabaLightGray,
    errorContainerColor = Color.White,
    cursorColor = BabaRed,
    errorCursorColor = BabaOverdue,
    focusedBorderColor = BabaRed,
    unfocusedBorderColor = BabaBorder,
    disabledBorderColor = BabaBorder,
    errorBorderColor = BabaOverdue,
    focusedLabelColor = BabaRed,
    unfocusedLabelColor = BabaGray,
    disabledLabelColor = BabaGray,
    errorLabelColor = BabaOverdue,
    focusedPlaceholderColor = BabaGray,
    unfocusedPlaceholderColor = BabaGray,
    disabledPlaceholderColor = BabaGray
)
