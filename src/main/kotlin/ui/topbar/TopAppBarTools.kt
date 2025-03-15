package ui.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TopAppBarTool() {
    var isHovered by remember { mutableStateOf(false) }
    var isHoveredDebug by remember { mutableStateOf(false) }


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = 10.dp,
                    bottomEnd = 10.dp
                )
            )
            .background(
                color = MaterialTheme.colorScheme.primary
            )
            .padding(
                all = 10.dp,
            ),

        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "Test name",
                color = MaterialTheme.colorScheme.onPrimary
            )

        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Icon(
                painter = painterResource("icons/run/run.svg"),
                contentDescription = null,

                tint = if (isHovered)
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onPrimary,

                modifier = Modifier
                    .clickable {

                    }

                    .onPointerEvent(PointerEventType.Enter) {
                        isHovered = true
                    }

                    .onPointerEvent(PointerEventType.Exit) {
                        isHovered = false
                    }
            )

            Icon(
                painter = painterResource("icons/debug/debug.svg"),
                contentDescription = null,

                tint = if (isHoveredDebug)
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onPrimary,

                modifier = Modifier
                    .clickable {

                    }

                    .onPointerEvent(PointerEventType.Enter) {
                        isHoveredDebug = true
                    }

                    .onPointerEvent(PointerEventType.Exit) {
                        isHoveredDebug = false
                    }
            )



        }

    }

}