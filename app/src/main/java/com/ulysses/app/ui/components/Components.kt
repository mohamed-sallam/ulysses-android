package com.ulysses.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ulysses.app.ui.theme.*

/**
 * Glassmorphic card with gradient border and subtle background.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = modifier
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = CardDark.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            SlateBlue.copy(alpha = 0.3f),
                            SlateBlue.copy(alpha = 0.1f)
                        )
                    ),
                    shape = shape
                )
                .padding(16.dp),
            content = content
        )
    }
}

/**
 * Animated shield icon for active session status.
 */
@Composable
fun PulsingShield(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shield_scale"
    )

    val color by animateColorAsState(
        targetValue = if (isActive) GoldenAmber else SteelGray,
        animationSpec = tween(600),
        label = "shield_color"
    )

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) Brush.radialGradient(
                        colors = listOf(
                            GoldenAmber.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ) else Brush.radialGradient(
                        colors = listOf(
                            SteelGray.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = "Protection status",
            modifier = Modifier.size(48.dp),
            tint = color
        )
    }
}

/**
 * Section header with optional action button.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = SilverMist,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.labelLarge,
                    color = GoldenAmber
                )
            }
        }
    }
}

/**
 * Permission setup item with status indicator.
 */
@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isGranted) ActiveGreen.copy(alpha = 0.3f) else SlateBlue.copy(alpha = 0.3f),
        label = "perm_border"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) SoftGreen.copy(alpha = 0.3f) else CardDark
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) ActiveGreen.copy(alpha = 0.15f)
                        else GoldenAmber.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Filled.CheckCircle else icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isGranted) ActiveGreen else GoldenAmber
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverMist,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Grant",
                    tint = SteelGray
                )
            }
        }
    }
}

/**
 * Emoji icon container for block items.
 */
@Composable
fun EmojiIcon(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Int = 44
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GoldenAmber.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = (size * 0.5f).sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Animated countdown timer display.
 */
@Composable
fun CountdownDisplay(
    remainingMillis: Long,
    modifier: Modifier = Modifier
) {
    val hours = remainingMillis / 3600000
    val minutes = (remainingMillis % 3600000) / 60000
    val seconds = (remainingMillis % 60000) / 1000

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeUnit(hours.toInt(), "h")
        Text(":", style = MaterialTheme.typography.displayMedium, color = GoldenAmber.copy(alpha = 0.5f))
        TimeUnit(minutes.toInt(), "m")
        Text(":", style = MaterialTheme.typography.displayMedium, color = GoldenAmber.copy(alpha = 0.5f))
        TimeUnit(seconds.toInt(), "s")
    }
}

@Composable
private fun TimeUnit(value: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = "%02d".format(value),
            style = MaterialTheme.typography.displayMedium,
            color = PureWhite,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SteelGray
        )
    }
}

/**
 * Empty state placeholder.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = SlateBlue
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = SilverMist,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = SteelGray,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber)
            ) {
                Text(text = actionLabel, color = DeepOcean, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
