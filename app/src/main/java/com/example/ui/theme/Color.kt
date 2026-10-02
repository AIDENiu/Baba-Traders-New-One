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
val BabaBorder = Color(0xFFBDBDBD) // Crisp high-contrast border so input boxes are clearly visible

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
    disabledPlaceholderColor = BabaGray,
    errorPlaceholderColor = BabaOverdue,
    focusedLeadingIconColor = BabaRed,
    unfocusedLeadingIconColor = BabaGray,
    disabledLeadingIconColor = BabaGray,
    errorLeadingIconColor = BabaOverdue,
    focusedTrailingIconColor = BabaGray,
    unfocusedTrailingIconColor = BabaGray,
    disabledTrailingIconColor = BabaGray,
    errorTrailingIconColor = BabaOverdue,
    focusedPrefixColor = BabaDark,
    unfocusedPrefixColor = BabaDark,
    focusedSuffixColor = BabaDark,
    unfocusedSuffixColor = BabaDark
)

