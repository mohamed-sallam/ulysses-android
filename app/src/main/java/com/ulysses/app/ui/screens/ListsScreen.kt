package com.ulysses.app.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import androidx.compose.foundation.Image
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.data.db.entities.BlockListEntity
import com.ulysses.app.data.db.entities.BlockListEntryEntity
import com.ulysses.app.data.repository.UlyssesRepository
import com.ulysses.app.ui.components.*
import com.ulysses.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { UlyssesDatabase.getInstance(context) }
    val repo = remember { UlyssesRepository(db, context) }
    val scope = rememberCoroutineScope()

    val lists by db.blockListDao().getAllLists().collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddWebDialog by remember { mutableStateOf<String?>(null) }
    var showAddAppDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepOcean, titleContentColor = PureWhite, navigationIconContentColor = PureWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = GoldenAmber, contentColor = DeepOcean, shape = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Filled.Add, "Create List") }
        },
        containerColor = DeepOcean
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(DeepOcean, DarkNavy))),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (lists.isEmpty()) {
                item { EmptyState(Icons.Outlined.FormatListBulleted, "No lists yet", "Create lists containing blocked or allowed websites and apps", "Create List", { showCreateDialog = true }) }
            } else {
                items(lists) { list -> 
                    ListCard(
                        list = list, 
                        db = db, 
                        onAddWeb = { showAddWebDialog = list.id }, 
                        onAddApp = { showAddAppDialog = list.id },
                        onDelete = { scope.launch { repo.deleteList(list) } }
                    ) 
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateListDialog({ showCreateDialog = false }, { name -> scope.launch { repo.createList(name); showCreateDialog = false } })
    }

    showAddWebDialog?.let { listId ->
        AddWebDialog({ showAddWebDialog = null }, { action, ruleType, value -> scope.launch { repo.addEntry(listId, action, ruleType, value); showAddWebDialog = null } })
    }

    showAddAppDialog?.let { listId ->
        AddAppDialog({ showAddAppDialog = null }, { action, packages -> 
            scope.launch { 
                packages.forEach { pkg -> repo.addEntry(listId, action, "app_name", pkg) }
                showAddAppDialog = null 
            }
        })
    }
}

@Composable
private fun ListCard(list: BlockListEntity, db: UlyssesDatabase, onAddWeb: () -> Unit, onAddApp: () -> Unit, onDelete: () -> Unit) {
    val entries by db.blockListDao().getEntriesForList(list.id).collectAsState(initial = emptyList())
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.List, null, Modifier.size(24.dp), tint = SilverMist)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(list.name, style = MaterialTheme.typography.titleSmall, color = PureWhite, fontWeight = FontWeight.SemiBold)
                Text("${entries.size} entries", style = MaterialTheme.typography.bodySmall, color = SteelGray)
            }
            IconButton(onClick = onAddWeb) { Icon(Icons.Filled.Language, "Add web rule", tint = GoldenAmber) }
            IconButton(onClick = onAddApp) { Icon(Icons.Filled.Android, "Add app rule", tint = ActiveGreen) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = BlockedRed.copy(alpha = 0.7f)) }
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                entries.forEach { entry -> EntryRow(entry, { scope.launch { db.blockListDao().deleteEntry(entry) } }) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: BlockListEntryEntity, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val typeColor = if (entry.type == "allow") ActiveGreen else BlockedRed
        val icon = when (entry.ruleType) { 
            "domain", "url", "wildcard" -> Icons.Filled.Language
            "app_name" -> Icons.Filled.Android
            else -> Icons.Filled.Circle 
        }
        Icon(icon, null, Modifier.size(16.dp), tint = typeColor)
        Spacer(Modifier.width(8.dp))
        Text(entry.value, style = MaterialTheme.typography.bodySmall, color = SilverMist, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        
        Text(entry.type.uppercase(), style = MaterialTheme.typography.labelSmall, color = typeColor)
        Spacer(Modifier.width(8.dp))
        
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "Remove", Modifier.size(14.dp), tint = SteelGray) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateListDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DarkNavy,
        title = { Text("Create List", color = PureWhite, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("List Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenAmber, unfocusedBorderColor = SlateBlue, cursorColor = GoldenAmber, focusedLabelColor = GoldenAmber, unfocusedLabelColor = SteelGray, focusedTextColor = PureWhite, unfocusedTextColor = CloudWhite))
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }, enabled = name.isNotBlank()) { Text("Create", color = GoldenAmber, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SteelGray) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWebDialog(onDismiss: () -> Unit, onAdd: (String, String, String) -> Unit) {
    var value by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf("deny") }
    var selectedType by remember { mutableStateOf("domain") }
    
    val types = listOf(
        "domain" to "Domain (e.g. facebook.com)", 
        "url" to "URL Path (e.g. reddit.com/r/funny)", 
        "wildcard" to "Wildcard (e.g. *gaming*)"
    )

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DarkNavy,
        title = { Text("Add Web Rule", color = PureWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = selectedAction == "deny", onClick = { selectedAction = "deny" }, label = { Text("Deny") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlockedRed.copy(alpha = 0.2f), selectedLabelColor = BlockedRed))
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = selectedAction == "allow", onClick = { selectedAction = "allow" }, label = { Text("Allow") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ActiveGreen.copy(alpha = 0.2f), selectedLabelColor = ActiveGreen))
                }
                Spacer(Modifier.height(12.dp))
                types.forEach { (type, label) ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { selectedType = type }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedType == type, onClick = { selectedType = type }, colors = RadioButtonDefaults.colors(selectedColor = GoldenAmber, unselectedColor = SteelGray))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall, color = if (selectedType == type) PureWhite else SilverMist)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Value") }, modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenAmber, unfocusedBorderColor = SlateBlue, cursorColor = GoldenAmber, focusedLabelColor = GoldenAmber, unfocusedLabelColor = SteelGray, focusedTextColor = PureWhite, unfocusedTextColor = CloudWhite))
            }
        },
        confirmButton = { TextButton(onClick = { if (value.isNotBlank()) onAdd(selectedAction, selectedType, value) }, enabled = value.isNotBlank()) { Text("Add", color = GoldenAmber, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SteelGray) } }
    )
}

data class AppInfoItem(val name: String, val packageName: String, val icon: android.graphics.drawable.Drawable?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAppDialog(onDismiss: () -> Unit, onAdd: (String, List<String>) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedPackages by remember { mutableStateOf(setOf<String>()) }
    var selectedAction by remember { mutableStateOf("deny") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply { addCategory(android.content.Intent.CATEGORY_LAUNCHER) }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            
            apps = resolveInfos.mapNotNull { ri ->
                if (ri.activityInfo.packageName == context.packageName) null
                else AppInfoItem(
                    name = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName,
                    icon = ri.activityInfo.loadIcon(pm)
                )
            }.sortedBy { it.name.lowercase() }
        }
    }

    val filteredApps = apps.filter { 
        it.name.contains(searchQuery, ignoreCase = true) || 
        it.packageName.contains(searchQuery, ignoreCase = true) 
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DarkNavy,
        modifier = Modifier.fillMaxHeight(0.9f),
        title = { Text("Select Apps", color = PureWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = selectedAction == "deny", onClick = { selectedAction = "deny" }, label = { Text("Deny") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BlockedRed.copy(alpha = 0.2f), selectedLabelColor = BlockedRed))
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = selectedAction == "allow", onClick = { selectedAction = "allow" }, label = { Text("Allow") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ActiveGreen.copy(alpha = 0.2f), selectedLabelColor = ActiveGreen))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it }, 
                    label = { Text("Search Apps") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, 
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenAmber, unfocusedBorderColor = SlateBlue, cursorColor = GoldenAmber, focusedLabelColor = GoldenAmber, unfocusedLabelColor = SteelGray, focusedTextColor = PureWhite, unfocusedTextColor = CloudWhite)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { 
                                selectedPackages = if (selectedPackages.contains(app.packageName)) selectedPackages - app.packageName else selectedPackages + app.packageName 
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = selectedPackages.contains(app.packageName), onCheckedChange = { 
                                selectedPackages = if (it) selectedPackages + app.packageName else selectedPackages - app.packageName 
                            }, colors = CheckboxDefaults.colors(checkedColor = GoldenAmber, uncheckedColor = SteelGray))
                            Spacer(Modifier.width(8.dp))
                            if (app.icon != null) {
                                Image(painter = rememberDrawablePainter(app.icon), contentDescription = null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                Text(app.name, style = MaterialTheme.typography.bodyMedium, color = PureWhite)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = SteelGray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(selectedAction, selectedPackages.toList()) }, enabled = selectedPackages.isNotEmpty()) { Text("Add ${selectedPackages.size}", color = GoldenAmber, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = SteelGray) } }
    )
}
