package com.derycode.srs.desktopteacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The teacher app's own palette.
 *
 * Deliberately calmer and larger than the admin console: this is used in a
 * staff room, often quickly, sometimes on an old laptop screen. Same DeryCode
 * teal family, but marks entry gets the whole window rather than a sidebar.
 */
object T {
    val BG = Color(0xFFF4F0E7)
    val CARD = Color(0xFFFBFAF7)
    val INK = Color(0xFF18352E)
    val MUTED = Color(0xFF6D7D75)
    val TEAL = Color(0xFF237B71)
    val TEAL_SOFT = Color(0xFFDFF0ED)
    val GOLD = Color(0xFFC59A4A)
    val RUST = Color(0xFFA85E48)
    val RUST_SOFT = Color(0xFFF4DFD8)
    val LINE = Color(0xFFD7DDD5)
    val DEEP = Color(0xFF112C26)
    val OK = Color(0xFF2E7D5B)
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(T.CARD, RoundedCornerShape(14.dp))
            .border(1.dp, T.LINE, RoundedCornerShape(14.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun H1(text: String, sub: String = "") {
    Column {
        Text(text, color = T.INK, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(sub, color = T.MUTED, fontSize = 13.sp)
        }
    }
}

@Composable
fun Label(text: String) {
    Text(text, color = T.MUTED, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
fun Tf(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    Box(
        modifier
            .background(Color.White, RoundedCornerShape(9.dp))
            .border(1.dp, T.LINE, RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty() && placeholder.isNotEmpty()) {
            Text(placeholder, color = T.MUTED, fontSize = 13.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = T.INK, fontSize = 13.sp)
        )
    }
}

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    kind: Kind = Kind.PRIMARY,
    modifier: Modifier = Modifier
) {
    val bg = when {
        !enabled -> T.LINE
        kind == Kind.PRIMARY -> T.TEAL
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> T.MUTED
        kind == Kind.PRIMARY -> Color.White
        else -> T.TEAL
    }
    Box(
        modifier
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, if (kind == Kind.PRIMARY) bg else T.TEAL, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .let { if (enabled) it.clickableNoRipple(onClick) else it },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

enum class Kind { PRIMARY, GHOST }

@Composable
fun Pill(text: String, color: Color = T.TEAL, soft: Color = T.TEAL_SOFT) {
    Box(
        Modifier.background(soft, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
    ) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(T.CARD, RoundedCornerShape(12.dp)).border(1.dp, T.LINE, RoundedCornerShape(12.dp)).padding(14.dp)) {
        Text(value, color = T.TEAL, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = T.MUTED, fontSize = 11.sp)
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
