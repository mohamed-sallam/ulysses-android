package com.ulysses.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ulysses.app.data.db.UlyssesDatabase
import com.ulysses.app.data.db.entities.BlockEntity
import com.ulysses.app.data.repository.UlyssesRepository
import com.ulysses.app.ui.components.*
import com.ulysses.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSessionScreen(blockId: String, onBack: () -> Unit, onStarted: () -> Unit) {
    val context = LocalContext.current
    val db = remember { UlyssesDatabase.getInstance(context) }
    val repo = remember { UlyssesRepository(db, context) }
    val scope = rememberCoroutineScope()

    val block by produceState<BlockEntity?>(null, blockId) { value = db.blockDao().getBlockById(blockId) }
    val lists by produceState<List<String>>(emptyList(), blockId) {
        val blockLists = db.blockDao().getListsForBlock(blockId)
        value = blockLists.map { it.name }
    }

    var lockType by remember { mutableStateOf("timer") }
    var durationMinutes by remember { mutableFloatStateOf(25f) }
    var password by remember { mutableStateOf("") }
    var propagate by remember { mutableStateOf(false) }

    fun actuallyStartSession() {
        val lockVal = if (lockType == "timer") (durationMinutes.toInt() * 60 * 1000).toString() else UlyssesRepository.hashPassword(password)
        scope.launch {
            repo.startSession(
                blockId = block!!.id,
                lockType = lockType,
                lockValue = lockVal,
                propagate = propagate
            )
            onStarted()
        }
    }

    val roleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (com.ulysses.app.protection.LauncherManager.isDefaultLauncher(context)) {
            actuallyStartSession()
        } else {
            // User aborted or didn't set it. Disable the component so we don't clutter their launcher choices.
            com.ulysses.app.protection.LauncherManager.disableLauncherComponent(context)
            android.widget.Toast.makeText(context, "Ulysses must be set as the default launcher to activate a block session.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun checkLauncherRoleAndStart() {
        val intent = com.ulysses.app.protection.LauncherManager.getLauncherRoleIntent(context)
        if (intent != null) {
            roleLauncher.launch(intent)
        } else {
            if (com.ulysses.app.protection.LauncherManager.isDefaultLauncher(context)) {
                actuallyStartSession()
            } else {
                com.ulysses.app.protection.LauncherManager.disableLauncherComponent(context)
                android.widget.Toast.makeText(context, "Ulysses must be set as the default launcher to activate a block session.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        checkLauncherRoleAndStart()
    }

    LaunchedEffect(block) {
        block?.let { lockType = it.defaultLockType }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start Session", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.Close, "Cancel") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepOcean, titleContentColor = PureWhite, navigationIconContentColor = PureWhite)
            )
        },
        containerColor = DeepOcean
    ) { padding ->
        if (block == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = GoldenAmber) }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(DeepOcean, DarkNavy))).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmojiIcon(emoji = block!!.iconEmoji, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(block!!.name, style = MaterialTheme.typography.headlineMedium, color = PureWhite, fontWeight = FontWeight.Bold)
            if (lists.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Enforcing: ${lists.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, color = SilverMist, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(32.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text("Lock Method", style = MaterialTheme.typography.titleSmall, color = GoldenAmber, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LockTypeSelector("Timer", Icons.Filled.Timer, lockType == "timer", { lockType = "timer" }, Modifier.weight(1f))
                    LockTypeSelector("Password", Icons.Filled.Lock, lockType == "password", { lockType = "password" }, Modifier.weight(1f))
                }

                Spacer(Modifier.height(24.dp))

                if (lockType == "timer") {
                    Text("Duration: ${durationMinutes.toInt()} minutes", color = CloudWhite, fontWeight = FontWeight.Medium)
                    Slider(
                        value = durationMinutes, onValueChange = { durationMinutes = it }, valueRange = 1f..120f, steps = 119,
                        colors = SliderDefaults.colors(thumbColor = GoldenAmber, activeTrackColor = GoldenAmber, inactiveTrackColor = SlateBlue)
                    )
                } else {
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Set Unlock Password") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GoldenAmber, unfocusedBorderColor = SlateBlue, cursorColor = GoldenAmber, focusedLabelColor = GoldenAmber, unfocusedLabelColor = SteelGray, focusedTextColor = PureWhite, unfocusedTextColor = CloudWhite)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Wifi, null, tint = GoldenAmber)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Propagate to Network", color = PureWhite, style = MaterialTheme.typography.titleSmall)
                        Text("Start this block on all paired devices", color = SilverMist, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = propagate, onCheckedChange = { propagate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = DeepOcean, checkedTrackColor = GoldenAmber, uncheckedThumbColor = SilverMist, uncheckedTrackColor = SlateBlue)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val vpnIntent = android.net.VpnService.prepare(context)
                    if (vpnIntent != null) {
                        vpnLauncher.launch(vpnIntent)
                    } else {
                        checkLauncherRoleAndStart()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber),
                enabled = lockType == "timer" || password.isNotBlank()
            ) {
                Icon(Icons.Filled.Shield, null, tint = DeepOcean, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Start Block Session", color = DeepOcean, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun LockTypeSelector(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) GoldenAmber.copy(alpha = 0.15f) else CardDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) GoldenAmber else SlateBlue)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = if (selected) GoldenAmber else SteelGray, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = if (selected) GoldenAmber else CloudWhite, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
