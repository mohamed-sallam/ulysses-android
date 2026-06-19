package com.ulysses.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.ulysses.app.UlyssesApp
import com.ulysses.app.network.DhtNetworkManager
import com.ulysses.app.ui.components.*
import com.ulysses.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(onBack: () -> Unit, onScanQr: () -> Unit) {
    val context = LocalContext.current
    val networkManager = remember { UlyssesApp.instance.networkManager }
    val scope = rememberCoroutineScope()

    var isInGroup by remember { mutableStateOf(networkManager.isInGroup()) }
    var peerCount by remember { mutableIntStateOf(0) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        if (result.contents != null) {
            val qrText = result.contents
            if (networkManager.joinGroup(qrText)) {
                isInGroup = true
            } else {
                android.widget.Toast.makeText(context, "Invalid QR code format", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Refresh peer count
    LaunchedEffect(isInGroup) {
        if (isInGroup) {
            while (true) {
                peerCount = networkManager.getConnectedPeers().size
                delay(5000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepOcean, titleContentColor = PureWhite, navigationIconContentColor = PureWhite)
            )
        },
        containerColor = DeepOcean
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(DeepOcean, DarkNavy)))
                .verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Status header
            if (isInGroup) {
                PulsingShield(isActive = true, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Connected to Group", style = MaterialTheme.typography.headlineSmall, color = PureWhite, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(ActiveGreen))
                    Spacer(Modifier.width(6.dp))
                    Text("$peerCount peer(s) online", style = MaterialTheme.typography.bodyMedium, color = ActiveGreen)
                }
            } else {
                Icon(Icons.Filled.Wifi, null, Modifier.size(72.dp), tint = SlateBlue)
                Spacer(Modifier.height(16.dp))
                Text("Cross-Device Sync", style = MaterialTheme.typography.headlineSmall, color = PureWhite, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Pair devices to sync triggers across your network via OpenDHT.", style = MaterialTheme.typography.bodyMedium, color = SilverMist, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(24.dp))

            // Actions
            GlassCard(Modifier.fillMaxWidth()) {
                if (isInGroup) {
                    Text("Group Actions", style = MaterialTheme.typography.titleSmall, color = GoldenAmber, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    // Show QR for others to join
                    Button(onClick = {
                        val info = networkManager.getGroupInfo()
                        if (info != null) {
                            qrBitmap = generateQrCode(info.toQrString())
                            showQrDialog = true
                        }
                    }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber)) {
                        Icon(Icons.Filled.QrCode, null, tint = DeepOcean, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Show Pairing QR Code", color = DeepOcean, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        networkManager.leaveGroup(); isInGroup = false
                    }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BlockedRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BlockedRed.copy(alpha = 0.5f))) {
                        Icon(Icons.Filled.ExitToApp, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Leave Group", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text("Join or Create", style = MaterialTheme.typography.titleSmall, color = GoldenAmber, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            val info = networkManager.createGroup()
                            isInGroup = true
                            qrBitmap = generateQrCode(info.toQrString())
                            showQrDialog = true
                        }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldenAmber)) {
                            Icon(Icons.Filled.Add, null, tint = DeepOcean, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Create Group", color = DeepOcean, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(onClick = { scanLauncher.launch(com.journeyapps.barcodescanner.ScanOptions().setPrompt("Scan group QR code to pair devices").setBeepEnabled(false)) }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldenAmber),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldenAmber.copy(alpha = 0.5f))) {
                            Icon(Icons.Filled.QrCodeScanner, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Scan QR", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // How it works
            GlassCard(Modifier.fillMaxWidth()) {
                Text("How It Works", style = MaterialTheme.typography.titleSmall, color = GoldenAmber, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                listOf(
                    "1. Create a group on one device" to "Generates a shared secret key via OpenDHT",
                    "2. Scan QR code on other devices" to "Securely shares the group key out-of-band",
                    "3. Triggers sync automatically" to "CRDT with Lamport timestamps via DHT",
                    "4. Fire events propagate instantly" to "All devices start block sessions together",
                    "5. Disable events unlock all" to "Legal unlock on one device frees all"
                ).forEach { (title, desc) ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Column {
                            Text(title, style = MaterialTheme.typography.bodySmall, color = CloudWhite, fontWeight = FontWeight.Medium)
                            Text(desc, style = MaterialTheme.typography.labelSmall, color = SteelGray)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = InfoBlue.copy(alpha = 0.1f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, InfoBlue.copy(alpha = 0.2f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, null, tint = InfoBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cross-device sync uses AES-256-GCM encryption via OpenDHT (Jami). Group key is shared only via QR code — never transmitted over the network.",
                        style = MaterialTheme.typography.bodySmall, color = InfoBlue.copy(alpha = 0.9f))
                }
            }
        }
    }

    // QR Code Dialog
    if (showQrDialog && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            containerColor = DarkNavy,
            title = { Text("Scan to Join Group", color = PureWhite, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Group QR Code",
                            modifier = Modifier.size(250.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Scan this code with Ulysses on another device to join the group", style = MaterialTheme.typography.bodySmall, color = SilverMist, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Done", color = GoldenAmber, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

/**
 * Generate a QR code bitmap from a string.
 */
private fun generateQrCode(content: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bitmap
    } catch (e: Exception) { null }
}
