package com.ulysses.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ElectricBolt
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
import com.ulysses.app.data.db.entities.TriggerEntity
import com.ulysses.app.data.repository.UlyssesRepository
import com.ulysses.app.ui.components.*
import com.ulysses.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { UlyssesDatabase.getInstance(context) }
    val repo = remember { UlyssesRepository(db, context) }
    val scope = rememberCoroutineScope()

    val triggers by db.triggerDao().getAllTriggers().collectAsState(initial = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }
    var triggerToFire by remember { mutableStateOf<String?>(null) }

    val roleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (com.ulysses.app.protection.LauncherManager.isDefaultLauncher(context)) {
            triggerToFire?.let { id -> scope.launch { repo.fireTrigger(id) } }
        } else {
            com.ulysses.app.protection.LauncherManager.disableLauncherComponent(context)
            android.widget.Toast.makeText(context, "Ulysses must be set as the default launcher.", android.widget.Toast.LENGTH_LONG).show()
        }
        triggerToFire = null
    }

    val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        val intent = com.ulysses.app.protection.LauncherManager.getLauncherRoleIntent(context)
        if (intent != null) {
            roleLauncher.launch(intent)
        } else {
            if (com.ulysses.app.protection.LauncherManager.isDefaultLauncher(context)) {
                triggerToFire?.let { id -> scope.launch { repo.fireTrigger(id) } }
            } else {
                com.ulysses.app.protection.LauncherManager.disableLauncherComponent(context)
                android.widget.Toast.makeText(context, "Ulysses must be set as the default launcher.", android.widget.Toast.LENGTH_LONG).show()
            }
            triggerToFire = null
        }
    }

    fun handleFire(triggerId: String) {
        triggerToFire = triggerId
        val vpnIntent = android.net.VpnService.prepare(context)
        if (vpnIntent != null) {
            vpnLauncher.launch(vpnIntent)
        } else {
            val intent = com.ulysses.app.protection.LauncherManager.getLauncherRoleIntent(context)
            if (intent != null) {
                roleLauncher.launch(intent)
            } else {
                if (com.ulysses.app.protection.LauncherManager.isDefaultLauncher(context)) {
                    scope.launch { repo.fireTrigger(triggerId) }
                    triggerToFire = null
                } else {
                    com.ulysses.app.protection.LauncherManager.disableLauncherComponent(context)
                    android.widget.Toast.makeText(context, "Ulysses must be set as the default launcher.", android.widget.Toast.LENGTH_LONG).show()
                    triggerToFire = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Triggers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepOcean, titleContentColor = PureWhite, navigationIconContentColor = PureWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = GoldenAmber, contentColor = DeepOcean, shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Filled.Add, "Create Trigger") }
        },
        containerColor = DeepOcean
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(DeepOcean, DarkNavy))),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val visibleTriggers = triggers.filter { !it.isDeleted }
            
            if (visibleTriggers.isEmpty()) {
                item { EmptyState(Icons.Outlined.ElectricBolt, "No triggers yet", "Create triggers to start sessions locally or across synced devices", "Create Trigger", { showCreateDialog = true }) }
            } else {
                items(visibleTriggers) { trigger ->
                    TriggerCard(trigger, db, { handleFire(trigger.id) }, { scope.launch { repo.deleteTrigger(trigger.id) } })
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateTriggerDialog(db, { showCreateDialog = false }, { name, blockId, propagate -> scope.launch { repo.createTrigger(name, blockId, propagate); showCreateDialog = false } })
    }
}

@Composable
private fun TriggerCard(trigger: TriggerEntity, db: UlyssesDatabase, onFire: () -> Unit, onDelete: () -> Unit) {
    val block by produceState<BlockEntity?>(null, trigger.blockId) { value = db.blockDao().getBlockById(trigger.blockId) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ElectricBolt, null, Modifier.size(24.dp), tint = GoldenAmber)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(trigger.name, style = MaterialTheme.typography.titleSmall, color = PureWhite, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fires: ${block?.name ?: "Unknown block"}", style = MaterialTheme.typography.bodySmall, color = SilverMist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (trigger.propagateToNetwork) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Wifi, null, Modifier.size(12.dp), tint = ActiveGreen)
                        Spacer(Modifier.width(4.dp))
                        Text("Synced", style = MaterialTheme.typography.labelSmall, color = ActiveGreen)
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = BlockedRed.copy(alpha = 0.7f)) }
            FilledIconButton(onClick = onFire, modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = GoldenAmber.copy(alpha = 0.15f), contentColor = GoldenAmber)) {
                Icon(Icons.Filled.PlayArrow, "Fire", Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTriggerDialog(db: UlyssesDatabase, onDismiss: () -> Unit, onCreate: (String, String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedBlockId by remember { mutableStateOf<String?>(null) }
    var propagate by remember { mutableStateOf(false) }
    val blocks by db.blockDao().getAllBlocks().collectAsState(initial = emptyList())
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DarkNavy,
        title = { Text("Create Trigger", color = PureWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Trigger Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenAmber, unfocusedBorderColor = SlateBlue, cursorColor = GoldenAmber, focusedLabelColor = GoldenAmber, unfocusedLabelColor = SteelGray, focusedTextColor = PureWhite, unfocusedTextColor = CloudWhite))
                Spacer(Modifier.height(16.dp))

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = blocks.find { it.id == selectedBlockId }?.name ?: "Select Block",
                        onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenAmber, unfocusedBorderColor = SlateBlue, focusedTextColor = PureWhite, unfocusedTextColor = CloudWhite)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(DarkNavy)) {
                        blocks.forEach { block ->
                            DropdownMenuItem(text = { Text(block.name, color = PureWhite) }, onClick = { selectedBlockId = block.id; expanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = propagate, onCheckedChange = { propagate = it }, colors = CheckboxDefaults.colors(checkedColor = GoldenAmber, uncheckedColor = SlateBlue, checkmarkColor = DeepOcean))
                    Text("Propagate to paired devices", color = CloudWhite, style = MaterialTheme.typography.bodyMedium)
                }
                Text("If checked, firing this trigger will start the session on all devices in your DHT network.", color = SilverMist, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 48.dp))
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank() && selectedBlockId != null) onCreate(name, selectedBlockId!!, propagate) }, enabled = name.isNotBlank() && selectedBlockId != null) { Text("Create", color = GoldenAmber, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SteelGray) } }
    )
}
