package com.ulysses.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.data.db.entities.BlockEntity
import com.ulysses.app.data.db.entities.BlockListEntity
import com.ulysses.app.data.repository.UlyssesRepository
import com.ulysses.app.ui.components.*
import com.ulysses.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Blocks management screen - create block templates with lists and lock methods.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(
    onBack: () -> Unit,
    onStartSession: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { UlyssesDatabase.getInstance(context) }
    val repo = remember { UlyssesRepository(db) }
    val scope = rememberCoroutineScope()

    val blocks by db.blockDao().getAllBlocks().collectAsState(initial = emptyList())
    val allLists by db.blockListDao().getAllLists().collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Blocks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepOcean,
                    titleContentColor = PureWhite,
                    navigationIconContentColor = PureWhite
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = GoldenAmber,
                contentColor = DeepOcean,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, "Create Block")
            }
        },
        containerColor = DeepOcean
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(DeepOcean, DarkNavy))),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (blocks.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Shield,
                        title = "No blocks yet",
                        description = "Create a block template to bundle your lists with a lock method",
                        actionLabel = "Create Block",
                        onAction = { showCreateDialog = true }
                    )
                }
            } else {
                items(blocks) { block ->
                    BlockCard(
                        block = block,
                        db = db,
                        onStart = { onStartSession(block.id) },
                        onDelete = {
                            scope.launch { repo.deleteBlock(block) }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateBlockDialog(
            availableLists = allLists,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, lockType, lockValue, emoji, listIds ->
                scope.launch {
                    repo.createBlock(name, desc, lockType, lockValue, emoji, listIds)
                    showCreateDialog = false
                }
            }
        )
    }
}

@Composable
private fun BlockCard(
    block: BlockEntity,
    db: UlyssesDatabase,
    onStart: () -> Unit,
    onDelete: () -> Unit
) {
    val lists by produceState(initialValue = emptyList<BlockListEntity>(), block.id) {
        value = db.blockDao().getListsForBlock(block.id)
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EmojiIcon(emoji = block.iconEmoji, size = 52)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite,
                    fontWeight = FontWeight.SemiBold
                )
                if (block.description.isNotBlank()) {
                    Text(
                        text = block.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = SteelGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (block.defaultLockType == "timer") Icons.Filled.Timer else Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = SteelGray
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (block.defaultLockType == "timer") {
                            val mins = block.defaultLockValue.toLongOrNull()?.div(60000) ?: 0
                            "${mins}min"
                        } else "Password",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteelGray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${lists.size} lists",
                        style = MaterialTheme.typography.labelSmall,
                        color = SteelGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDelete) {
                Text("Delete", color = BlockedRed.copy(alpha = 0.7f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber)
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp), tint = DeepOcean)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start Session", color = DeepOcean, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateBlockDialog(
    availableLists: List<BlockListEntity>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var lockType by remember { mutableStateOf("timer") }
    var timerMinutes by remember { mutableStateOf("60") }
    var password by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🛡️") }
    var selectedListIds by remember { mutableStateOf(setOf<String>()) }

    val emojis = listOf("🛡️", "🔒", "📵", "🚫", "⏰", "🎯", "💪", "🧘", "📚", "💼")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkNavy,
        title = { Text("Create Block", color = PureWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Block Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldenAmber,
                        unfocusedBorderColor = SlateBlue,
                        cursorColor = GoldenAmber,
                        focusedLabelColor = GoldenAmber,
                        unfocusedLabelColor = SteelGray,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = CloudWhite
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldenAmber,
                        unfocusedBorderColor = SlateBlue,
                        cursorColor = GoldenAmber,
                        focusedLabelColor = GoldenAmber,
                        unfocusedLabelColor = SteelGray,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = CloudWhite
                    )
                )

                // Emoji picker
                Text("Icon", style = MaterialTheme.typography.labelMedium, color = SilverMist)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    emojis.forEach { e ->
                        FilterChip(
                            selected = emoji == e,
                            onClick = { emoji = e },
                            label = { Text(e) },
                            modifier = Modifier.size(42.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldenAmber.copy(alpha = 0.2f)
                            )
                        )
                    }
                }

                // Lock type
                Text("Lock Method", style = MaterialTheme.typography.labelMedium, color = SilverMist)
                Row {
                    FilterChip(
                        selected = lockType == "timer",
                        onClick = { lockType = "timer" },
                        label = { Text("Timer") },
                        leadingIcon = { Icon(Icons.Filled.Timer, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldenAmber.copy(alpha = 0.2f),
                            selectedLabelColor = GoldenAmber
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = lockType == "password",
                        onClick = { lockType = "password" },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldenAmber.copy(alpha = 0.2f),
                            selectedLabelColor = GoldenAmber
                        )
                    )
                }

                if (lockType == "timer") {
                    OutlinedTextField(
                        value = timerMinutes,
                        onValueChange = { timerMinutes = it.filter { c -> c.isDigit() } },
                        label = { Text("Duration (minutes)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldenAmber,
                            unfocusedBorderColor = SlateBlue,
                            cursorColor = GoldenAmber,
                            focusedLabelColor = GoldenAmber,
                            unfocusedLabelColor = SteelGray,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = CloudWhite
                        )
                    )
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldenAmber,
                            unfocusedBorderColor = SlateBlue,
                            cursorColor = GoldenAmber,
                            focusedLabelColor = GoldenAmber,
                            unfocusedLabelColor = SteelGray,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = CloudWhite
                        )
                    )
                }

                // Lists selection
                if (availableLists.isNotEmpty()) {
                    Text("Attach Lists", style = MaterialTheme.typography.labelMedium, color = SilverMist)
                    availableLists.forEach { list ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedListIds = if (selectedListIds.contains(list.id)) {
                                        selectedListIds - list.id
                                    } else {
                                        selectedListIds + list.id
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedListIds.contains(list.id),
                                onCheckedChange = {
                                    selectedListIds = if (it) selectedListIds + list.id
                                    else selectedListIds - list.id
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = GoldenAmber,
                                    uncheckedColor = SteelGray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = list.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = CloudWhite
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val lockValue = if (lockType == "timer") {
                            ((timerMinutes.toLongOrNull() ?: 60) * 60000).toString()
                        } else {
                            UlyssesRepository.hashPassword(password)
                        }
                        onCreate(name, description, lockType, lockValue, emoji, selectedListIds.toList())
                    }
                },
                enabled = name.isNotBlank() && (lockType == "timer" || password.isNotBlank())
            ) {
                Text("Create", color = GoldenAmber, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SteelGray)
            }
        }
    )
}
