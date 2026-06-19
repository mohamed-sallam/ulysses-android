package com.ulysses.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.data.db.entities.BlockEntity
import com.ulysses.app.data.db.entities.BlockSessionEntity
import com.ulysses.app.data.repository.UlyssesRepository
import com.ulysses.app.ui.components.*
import com.ulysses.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBlocks: () -> Unit,
    onNavigateToLists: () -> Unit,
    onNavigateToTriggers: () -> Unit,
    onNavigateToNetwork: () -> Unit,
    onNavigateToStartSession: (String) -> Unit,
    onNavigateToSetup: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { UlyssesDatabase.getInstance(context) }
    val repo = remember { UlyssesRepository(db, context) }
    val scope = rememberCoroutineScope()

    val activeSessions by db.sessionDao().getActiveSessions().collectAsState(initial = emptyList())
    val blocks by db.blockDao().getAllBlocks().collectAsState(initial = emptyList())

    // Live countdown
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val activeValidSessions = activeSessions.filter { session ->
        if (session.lockType == "timer" && session.endsAt != null) now < session.endsAt
        else true
    }
    val hasActiveSession = activeValidSessions.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepOcean, DarkNavy))),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Ulysses", style = MaterialTheme.typography.headlineLarge, color = PureWhite, fontWeight = FontWeight.Bold)
                    Text(if (hasActiveSession) "Protection active" else "Ready to protect", style = MaterialTheme.typography.bodyMedium, color = if (hasActiveSession) GoldenAmber else SilverMist)
                }
                PulsingShield(isActive = hasActiveSession)
            }
        }

        // ── Active Session Card ──
        if (hasActiveSession) {
            items(activeValidSessions) { session ->
                ActiveSessionCard(session = session, now = now, db = db, onUnlock = { pw ->
                    scope.launch { repo.endSession(session.id, pw) }
                })
            }
        } else {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(GoldenAmber.copy(alpha = 0.2f), DeepAmber.copy(alpha = 0.1f)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(32.dp), tint = GoldenAmber)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start a Block Session", style = MaterialTheme.typography.titleMedium, color = PureWhite, fontWeight = FontWeight.SemiBold)
                            Text("Choose a block template to begin", style = MaterialTheme.typography.bodySmall, color = SilverMist)
                        }
                    }
                }
            }
        }

        // ── Quick Actions ──
        item { SectionHeader(title = "QUICK ACTIONS") }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { QuickActionChip(Icons.Filled.FormatListBulleted, "Lists", onNavigateToLists) }
                item { QuickActionChip(Icons.Filled.Shield, "Blocks", onNavigateToBlocks) }
                item { QuickActionChip(Icons.Filled.ElectricBolt, "Triggers", onNavigateToTriggers) }
                item { QuickActionChip(Icons.Filled.Wifi, "Network", onNavigateToNetwork) }
                item { QuickActionChip(Icons.Filled.Settings, "Setup", onNavigateToSetup) }
            }
        }

        // ── Blocks Overview ──
        item { SectionHeader("YOUR BLOCKS", action = "See All", onAction = onNavigateToBlocks) }

        if (blocks.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.Shield, "No blocks created", "Create a block template to start blocking distractions", "Create Block", onNavigateToBlocks)
            }
        } else {
            items(blocks.take(5)) { block ->
                BlockPreviewCard(block, { onNavigateToStartSession(block.id) }, onNavigateToBlocks)
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveSessionCard(session: BlockSessionEntity, now: Long, db: UlyssesDatabase, onUnlock: (String?) -> Unit) {
    val block by produceState<BlockEntity?>(null, session.blockId) { value = db.blockDao().getBlockById(session.blockId) }
    val remaining = if (session.endsAt != null) (session.endsAt - now).coerceAtLeast(0) else null

    var showPasswordDialog by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(ActiveGreen))
            Spacer(Modifier.width(12.dp))
            Text("Active Session", style = MaterialTheme.typography.labelLarge, color = ActiveGreen, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (session.lockType == "password") {
                TextButton(onClick = { showPasswordDialog = true }) {
                    Text("Unlock", color = GoldenAmber, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(block?.name ?: "Block Session", style = MaterialTheme.typography.titleLarge, color = PureWhite, fontWeight = FontWeight.Bold)

        if (remaining != null) {
            Spacer(Modifier.height(16.dp))
            CountdownDisplay(remainingMillis = remaining, modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = GoldenAmber)
                Spacer(Modifier.width(6.dp))
                Text("Password locked", style = MaterialTheme.typography.bodyMedium, color = GoldenAmber)
            }
        }
    }

    if (showPasswordDialog) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            containerColor = DarkNavy,
            title = { Text("Unlock Session", color = PureWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenAmber, unfocusedBorderColor = SlateBlue, cursorColor = GoldenAmber, focusedLabelColor = GoldenAmber, unfocusedLabelColor = SteelGray, focusedTextColor = PureWhite, unfocusedTextColor = CloudWhite)
                )
            },
            confirmButton = {
                TextButton(onClick = { onUnlock(pw); showPasswordDialog = false }) { Text("Unlock", color = GoldenAmber, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel", color = SteelGray) }
            }
        )
    }
}

@Composable
private fun BlockPreviewCard(block: BlockEntity, onStart: () -> Unit, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EmojiIcon(emoji = block.iconEmoji)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(block.name, style = MaterialTheme.typography.titleSmall, color = PureWhite, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(block.description.ifBlank { block.defaultLockType.replaceFirstChar { it.uppercase() } + " lock" }, style = MaterialTheme.typography.bodySmall, color = SteelGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledIconButton(onClick = onStart, modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = GoldenAmber.copy(alpha = 0.15f), contentColor = GoldenAmber)) {
                Icon(Icons.Filled.PlayArrow, "Start", Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun QuickActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = CardDark)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = GoldenAmber)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = CloudWhite)
        }
    }
}
