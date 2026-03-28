package com.gemini.voicekeyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val keyRows = listOf(
    listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
    listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'),
    listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M')
)

@Composable
fun QwertyKeyboard(
    onKeyClick: (Char) -> Unit,
    onDelete: () -> Unit,
    onSpace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF1F3F4))
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keyRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { char ->
                    KeyButton(
                        label = char.toString(),
                        onClick = { onKeyClick(char) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.5.dp)
                    )
                }
            }
        }

        // Bottom row: delete, space, enter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyButton(
                label = "⌫",
                onClick = onDelete,
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .padding(1.5.dp)
            )

            KeyButton(
                label = "space",
                onClick = onSpace,
                modifier = Modifier
                    .weight(5f)
                    .height(48.dp)
                    .padding(1.5.dp),
                fontSize = 14.sp
            )

            KeyButton(
                label = "⏎",
                onClick = { onKeyClick('\n') },
                modifier = Modifier
                    .weight(1.5f)
                    .height(48.dp)
                    .padding(1.5.dp)
            )
        }
    }
}

@Composable
fun KeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed = interactionSource.collectIsPressedAsState()

    val bgColor = if (isPressed.value) Color(0xFFDADCE0) else Color.White
    val textColor = Color(0xFF202124)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}
