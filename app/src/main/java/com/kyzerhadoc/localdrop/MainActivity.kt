package com.kyzerhadoc.localdrop

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.SoftApConfiguration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject

private const val TCP_PORT = 46789
private const val QR_PREFIX = "HADOC_LOCALDROP"
private val AppBackground = Color(0xFF080A10)
private val AppPanel = Color(0xFF121722)
private val AppPanel2 = Color(0xFF192231)
private val Accent = Color(0xFFF04452)
private val AccentLight = Color(0xFFFF7C87)
private val Muted = Color(0xFF97A4B7)
private val MainText = Color(0xFFF6F7FA)
private val Success = Color(0xFF4BD49A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 10, 16)
        window.navigationBarColor = android.graphics.Color.rgb(8, 10, 16)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent,
                secondary = AccentLight,
                background = AppBackground,
                surface = AppPanel,
                onPrimary = Color.White,
                onBackground = MainText,
                onSurface = MainText
            )) {
                LocalDropApp()
            }
        }
    }
}

private data class ChatEntry(
    val id: Long,
    val text: String? = null,
    val fileName: String? = null,
    val fileSize: Long = -1L,
    val transferId: Long? = null,
    val mine: Boolean,
    val filePath: String? = null
)

private data class TransferUi(
    val name: String,
    val mine: Boolean,
    val total: Long,
    val transferred: Long,
    val status: String,
    val startedAt: Long
)

@Composable
private fun LocalDropApp() {
    val context = LocalContext.current
    var page by remember { mutableStateOf("home") }
    var qrPayload by remember { mutableStateOf("") }
    var hotspotName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready to connect") }
    var connected by remember { mutableStateOf(false) }
    var peerName by remember { mutableStateOf("Nearby phone") }
    var draft by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatEntry>() }
    val transfers = remember { mutableStateMapOf<Long, TransferUi>() }
    val listState = rememberLazyListState()

    val engine = remember {
        LocalLanEngine(context, object : LocalLanEngine.Callbacks {
            override fun onStatus(message: String, isConnected: Boolean) {
                status = message
                connected = isConnected
                if (isConnected) {
                    page = "chat"
                } else if (page == "chat") {
                    page = "home"
                    qrPayload = ""
                    messages.clear()
                    transfers.clear()
                }
            }

            override fun onQrReady(payload: String, ssid: String) {
                qrPayload = payload
                hotspotName = ssid
                status = "Hotspot ready • waiting for one scan"
                page = "host"
            }

            override fun onTextReceived(text: String) {
                messages.add(ChatEntry(
                    id = System.nanoTime(),
                    text = text,
                    mine = false
                ))
            }

            override fun onPeerConnected(name: String) {
                peerName = name
                page = "chat"
                connected = true
            }

            override fun onTransferCreated(id: Long, name: String, size: Long, mine: Boolean) {
                val index = messages.indexOfFirst { it.transferId == id }
                val entry = ChatEntry(
                    id = id,
                    fileName = name,
                    fileSize = size,
                    transferId = id,
                    mine = mine
                )
                if (index >= 0) messages[index] = entry else messages.add(entry)
                val old = transfers[id]
                transfers[id] = TransferUi(
                    name = name,
                    mine = mine,
                    total = if (size > 0) size else (old?.total ?: -1L),
                    transferred = old?.transferred ?: 0L,
                    status = old?.status ?: "Preparing",
                    startedAt = old?.startedAt ?: System.currentTimeMillis()
                )
            }

            override fun onTransferProgress(
                id: Long,
                mine: Boolean,
                transferred: Long,
                total: Long,
                transferStatus: String
            ) {
                val old = transfers[id]
                transfers[id] = TransferUi(
                    name = old?.name ?: "Receiving file…",
                    mine = mine,
                    total = if (total > 0) total else (old?.total ?: -1L),
                    transferred = transferred,
                    status = transferStatus,
                    startedAt = old?.startedAt ?: System.currentTimeMillis()
                )
            }

            override fun onFileReceived(id: Long, path: String, size: Long) {
                val index = messages.indexOfFirst { it.transferId == id }
                if (index >= 0) {
                    messages[index] = messages[index].copy(filePath = path, fileSize = size)
                }
            }
        })
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (!connected) {
                Toast.makeText(context, "Connect the other phone first", Toast.LENGTH_SHORT).show()
            } else {
                uris.forEach { engine.sendFile(it) }
            }
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val scanned = result.contents
        if (!scanned.isNullOrBlank()) {
            qrPayload = ""
            page = "connecting"
            if (scanned.startsWith("$QR_PREFIX|")) {
                status = "Requesting temporary Wi-Fi connection…"
                engine.joinFromQr(scanned)
            } else {
                status = "Connecting to your PC…"
                engine.connectToPc(scanned)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allowed = results.values.all { it }
        if (!allowed) {
            Toast.makeText(context, "Nearby Wi-Fi permission is needed for QR pairing", Toast.LENGTH_LONG).show()
            page = "home"
        } else {
            when (pendingAction) {
                "host" -> {
                    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    if (!wifi.isWifiEnabled) {
                        Toast.makeText(context, "Turn Wi-Fi on, then tap Show QR again", Toast.LENGTH_LONG).show()
                        try {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                        }
                    } else {
                        page = "starting"
                        status = "Starting your private hotspot…"
                        engine.startHost()
                    }
                }
                "scan" -> {
                    val options = ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Scan the other phone's HADOC LocalDrop QR")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                    scannerLauncher.launch(options)
                }
            }
        }
        pendingAction = ""
    }

    fun requestWifiPermission(action: String) {
        pendingAction = action
        val required = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            if (action == "host") {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (!wifi.isWifiEnabled) {
                    Toast.makeText(context, "Turn Wi-Fi on, then tap Show QR again", Toast.LENGTH_LONG).show()
                    try {
                        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                } else {
                    page = "starting"
                    status = "Starting your private hotspot…"
                    engine.startHost()
                }
            } else {
                val options = ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Scan the other phone's HADOC LocalDrop QR")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                scannerLauncher.launch(options)
            }
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    DisposableEffect(engine) {
        onDispose { engine.close() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && page == "chat") {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        TopBar(
            connected = connected,
            peerName = peerName,
            showBack = page != "home" && page != "chat",
            onBack = {
                engine.cancelSession()
                page = "home"
                qrPayload = ""
                connected = false
            }
        )

        when (page) {
            "home" -> HomePage(
                onShowQr = { requestWifiPermission("host") },
                onScanQr = { requestWifiPermission("scan") },
                onConnectPc = {
                    val options = ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Scan the QR code shown by HADOC LocalDrop on your PC")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                    scannerLauncher.launch(options)
                }
            )
            "starting" -> ProgressPage(
                title = "Preparing your hotspot",
                message = "LocalDrop is creating a private Wi-Fi connection for the other phone.",
                status = status,
                onCancel = {
                    engine.cancelSession()
                    page = "home"
                }
            )
            "host" -> HostQrPage(
                qrPayload = qrPayload,
                hotspotName = hotspotName,
                onCancel = {
                    engine.cancelSession()
                    page = "home"
                    qrPayload = ""
                }
            )
            "connecting" -> ProgressPage(
                title = "Connecting to phone",
                message = "Approve the Wi-Fi connection prompt from Android to join the hotspot.",
                status = status,
                onCancel = {
                    engine.cancelSession()
                    page = "home"
                }
            )
            "chat" -> ChatPage(
                peerName = peerName,
                messages = messages,
                transfers = transfers,
                draft = draft,
                connected = connected,
                listState = listState,
                onDraftChange = { draft = it },
                onSend = {
                    val value = draft.trimEnd()
                    if (value.isNotBlank() && connected) {
                        messages.add(ChatEntry(id = System.nanoTime(), text = value, mine = true))
                        engine.sendText(value)
                        draft = ""
                    }
                },
                onAddFiles = { filePicker.launch(arrayOf("*/*")) },
                onOpenFile = { openReceivedFile(context, it) },
                onDisconnect = {
                    engine.cancelSession()
                    page = "home"
                    connected = false
                    messages.clear()
                    transfers.clear()
                }
            )
        }
    }
}

@Composable
private fun TopBar(
    connected: Boolean,
    peerName: String,
    showBack: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E131D))
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MainText)
            }
        }
        Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_localdrop),
            contentDescription = "HADOC LocalDrop",
            modifier = Modifier.size(47.dp)
        )
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text("HADOC LocalDrop", color = MainText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                if (connected) "CONNECTED  •  " + peerName.uppercase() else "MOBILE  ↔  MOBILE  •  WI-FI ONLY",
                color = if (connected) Success else Muted,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp
            )
        }
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            tint = if (connected) Success else Accent,
            modifier = Modifier.size(23.dp)
        )
    }
    HorizontalDivider(color = Color(0xFF222B39), thickness = 1.dp)
}

@Composable
private fun HomePage(
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onConnectPc: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_localdrop),
            contentDescription = "HADOC LocalDrop",
            modifier = Modifier.size(132.dp)
        )
        Text(
            "Share files. Instantly.",
            color = MainText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 15.dp)
        )
        Text(
            "Connect two phones or a Windows PC over your local network.",
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 26.dp)
        )

        Button(
            onClick = onShowQr,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Icon(Icons.Default.WifiTethering, contentDescription = null, modifier = Modifier.size(23.dp))
            Text("  Show QR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(13.dp))

        Button(
            onClick = onScanQr,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppPanel2),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(23.dp))
            Text("  Scan QR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(13.dp))

        Button(
            onClick = onConnectPc,
            modifier = Modifier.fillMaxWidth().height(62.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202B3B)),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(23.dp))
            Text("  Connect PC", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 27.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppPanel)
        ) {
            Row(
                modifier = Modifier.padding(15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = AccentLight, modifier = Modifier.size(25.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Private Wi-Fi connection", color = MainText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "Show QR or Scan QR for phone sharing. Connect PC scans your Windows app QR.",
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        Text(
            "MOBILE ↔ MOBILE  •  MOBILE ↔ PC  •  LOCAL NETWORK",
            color = Color(0xFF647186),
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}

@Composable
private fun HostQrPage(
    qrPayload: String,
    hotspotName: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Your hotspot is ready", color = MainText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "Scan this code from the second phone to connect.",
            color = Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp, bottom = 20.dp)
        )
        Card(
            shape = RoundedCornerShape(25.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Box(Modifier.padding(17.dp), contentAlignment = Alignment.Center) {
                val bitmap = rememberQrBitmap(qrPayload)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "One-time HADOC LocalDrop connection QR code",
                        modifier = Modifier.size(245.dp)
                    )
                } else {
                    CircularProgressIndicator(color = Accent)
                }
            }
        }
        Text(
            hotspotName,
            color = MainText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(
            "ONE DEVICE  •  ONE-TIME QR",
            color = AccentLight,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.3.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            "Keep this screen open. The hotspot stays active until the phone connects or you cancel.",
            color = Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 10.dp)
        )
        TextButtonLike("Cancel hotspot", onCancel, modifier = Modifier.padding(top = 22.dp))
    }
}

@Composable
private fun rememberQrBitmap(payload: String): androidx.compose.ui.graphics.ImageBitmap? {
    return remember(payload) {
        if (payload.isBlank()) null else try {
            val matrix = QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                560,
                560,
                mapOf(EncodeHintType.MARGIN to 2)
            )
            val bitmap = android.graphics.Bitmap.createBitmap(
                matrix.width,
                matrix.height,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
private fun ProgressPage(
    title: String,
    message: String,
    status: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(54.dp))
        Text(title, color = MainText, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 23.dp))
        Text(message, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp, modifier = Modifier.padding(top = 10.dp))
        Text(status, color = AccentLight, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
        TextButtonLike("Cancel", onCancel, modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = AppPanel2,
        shape = RoundedCornerShape(13.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF344156))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 21.dp, vertical = 12.dp),
            color = MainText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ChatPage(
    peerName: String,
    messages: List<ChatEntry>,
    transfers: Map<Long, TransferUi>,
    draft: String,
    connected: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddFiles: () -> Unit,
    onOpenFile: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF10251E)).padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(Success, CircleShape))
            Text("Connected to " + peerName, color = Color(0xFFA2E7C5), fontSize = 12.sp, modifier = Modifier.padding(start = 9.dp))
            Spacer(Modifier.weight(1f))
            TextButtonLike("Disconnect", onDisconnect)
        }

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = Accent, modifier = Modifier.size(40.dp))
                    Text("Your private space", color = MainText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                    Text("Send a message or share files.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                items(messages, key = { it.id }) { entry ->
                    MessageBubble(entry, entry.transferId?.let { transfers[it] }, onOpenFile)
                }
            }
        }

        HorizontalDivider(color = Color(0xFF222B39))
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF0E131D)).padding(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAddFiles,
                enabled = connected,
                modifier = Modifier.size(49.dp).background(AppPanel2, RoundedCornerShape(15.dp))
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Add files", tint = AccentLight)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                placeholder = { Text("Message…", color = Muted, fontSize = 14.sp) },
                minLines = 1,
                maxLines = 4,
                enabled = connected,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            IconButton(
                onClick = onSend,
                enabled = connected && draft.isNotBlank(),
                modifier = Modifier.size(49.dp).background(
                    if (connected && draft.isNotBlank()) Accent else Color(0xFF37313B),
                    RoundedCornerShape(15.dp)
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MessageBubble(
    entry: ChatEntry,
    transfer: TransferUi?,
    onOpenFile: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (entry.mine) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (entry.mine) 18.dp else 5.dp,
                bottomEnd = if (entry.mine) 5.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(containerColor = if (entry.mine) Color(0xFF52202C) else AppPanel)
        ) {
            Column(Modifier.padding(13.dp)) {
                if (entry.text != null) {
                    Text(entry.text, color = MainText, fontSize = 14.sp, lineHeight = 20.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = AccentLight)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(entry.fileName ?: "File", color = MainText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(if (entry.fileSize > 0) formatBytes(entry.fileSize) else "File transfer", color = Muted, fontSize = 11.sp)
                        }
                        if (entry.filePath != null) {
                            Button(
                                onClick = { onOpenFile(entry.filePath) },
                                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 5.dp)
                            ) {
                                Text("Open", fontSize = 11.sp)
                            }
                        }
                    }
                    if (transfer != null) {
                        val fraction = if (transfer.total > 0) {
                            (transfer.transferred.toFloat() / transfer.total.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            color = Accent,
                            trackColor = Color(0xFF3A3540)
                        )
                        val elapsed = (System.currentTimeMillis() - transfer.startedAt).coerceAtLeast(1L)
                        val speed = transfer.transferred * 1000L / elapsed
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(transfer.status, color = Muted, fontSize = 10.sp)
                            Text(
                                if (transfer.status == "Complete") "Complete"
                                else formatBytes(speed) + "/s • " + (fraction * 100).toInt() + "%",
                                color = AccentLight,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openReceivedFile(context: Context, path: String) {
    try {
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, "com.kyzerhadoc.localdrop.files", file)
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Open received file"))
    } catch (_: Exception) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return bytes.toString() + " B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

private class LocalLanEngine(
    context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onStatus(message: String, isConnected: Boolean)
        fun onQrReady(payload: String, ssid: String)
        fun onTextReceived(text: String)
        fun onPeerConnected(name: String)
        fun onTransferCreated(id: Long, name: String, size: Long, mine: Boolean)
        fun onTransferProgress(id: Long, mine: Boolean, transferred: Long, total: Long, status: String)
        fun onFileReceived(id: Long, path: String, size: Long)
    }

    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val outputLock = Any()
    private val sessionLock = Any()
    private val deviceId = UUID.randomUUID().toString()
    private val deviceName = (Build.MODEL ?: "Android phone").take(40)
    private val transferIds = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() shl 8)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .build()
    private val pcDownloadedFileIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var pcMode = false
    @Volatile private var pcBaseUrl: String? = null
    @Volatile private var pcToken: String? = null
    @Volatile private var pcName = "Your PC"
    @Volatile private var pcWebSocket: WebSocket? = null
    @Volatile private var pcPollTask: Runnable? = null

    @Volatile private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    @Volatile private var hostServer: ServerSocket? = null
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var output: DataOutputStream? = null
    @Volatile private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var hostToken: String? = null
    @Volatile private var tokenConsumed = false
    @Volatile private var closed = false
    @Volatile private var isHost = false
    @Volatile private var isJoining = false

    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

    fun startHost() {
        cancelSession()
        closed = false
        isHost = true
        isJoining = false
        hostToken = UUID.randomUUID().toString()
        tokenConsumed = false
        post { callbacks.onStatus("Starting a temporary local Wi-Fi hotspot…", false) }

        try {
            wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                    hotspotReservation = reservation
                    val configuration = try {
                        if (Build.VERSION.SDK_INT >= 30) {
                            val config = reservation.softApConfiguration
                            val ssid = config.ssid ?: ""
                            val passphrase = config.passphrase ?: ""
                            val security = when (config.securityType) {
                                SoftApConfiguration.SECURITY_TYPE_OPEN -> "OPEN"
                                SoftApConfiguration.SECURITY_TYPE_WPA3_SAE -> "WPA3"
                                else -> "WPA2"
                            }
                            Triple(ssid, passphrase, security)
                        } else {
                            @Suppress("DEPRECATION")
                            val config = reservation.wifiConfiguration
                            val ssid = config?.SSID?.trim('"') ?: ""
                            val passphrase = config?.preSharedKey?.trim('"') ?: ""
                            Triple(ssid, passphrase, if (passphrase.isBlank()) "OPEN" else "WPA2")
                        }
                    } catch (error: Exception) {
                        post { callbacks.onStatus("Could not read hotspot details: " + (error.message ?: "error"), false) }
                        cancelSession()
                        return
                    }

                    if (configuration.first.isBlank()) {
                        post { callbacks.onStatus("Android did not provide a hotspot name. Try again.", false) }
                        cancelSession()
                        return
                    }

                    val token = hostToken ?: return
                    val qr = buildQrPayload(configuration.first, configuration.second, configuration.third, token)
                    io.execute {
                        try {
                            val server = ServerSocket()
                            server.reuseAddress = true
                            server.bind(InetSocketAddress(TCP_PORT), 2)
                            hostServer = server
                            post { callbacks.onQrReady(qr, configuration.first) }
                            acceptSinglePhone(server, token)
                        } catch (error: Exception) {
                            post { callbacks.onStatus("Could not open LocalDrop connection: " + (error.message ?: "port busy"), false) }
                            cancelSession()
                        }
                    }
                }

                override fun onStopped() {
                    post { callbacks.onStatus("Android stopped the temporary hotspot.", false) }
                    cancelSession()
                }

                override fun onFailed(reason: Int) {
                    val message = when (reason) {
                        ERROR_INCOMPATIBLE_MODE -> "Hotspot could not start. Turn off your normal hotspot and try again."
                        ERROR_TETHERING_DISALLOWED -> "This device or carrier does not allow an app-created local hotspot."
                        ERROR_NO_CHANNEL -> "No Wi-Fi channel is available. Turn Wi-Fi off and on, then retry."
                        else -> "Could not start the hotspot (error " + reason + "). Try again."
                    }
                    post { callbacks.onStatus(message, false) }
                    cancelSession()
                }
            }, mainHandler)
        } catch (error: SecurityException) {
            post { callbacks.onStatus("Allow Nearby Wi-Fi permission and try again.", false) }
            cancelSession()
        } catch (error: Exception) {
            post { callbacks.onStatus("Hotspot error: " + (error.message ?: "unknown error"), false) }
            cancelSession()
        }
    }

    private fun buildQrPayload(ssid: String, password: String, security: String, token: String): String {
        fun encode(value: String): String = Base64.encodeToString(
            value.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return QR_PREFIX + "|1|" + encode(ssid) + "|" + encode(password) + "|" + security + "|" + token
    }

    private data class JoinInfo(
        val ssid: String,
        val password: String,
        val security: String,
        val token: String
    )

    private fun parseQr(payload: String): JoinInfo? {
        return try {
            val parts = payload.trim().split("|", limit = 6)
            if (parts.size != 6 || parts[0] != QR_PREFIX || parts[1] != "1") return null
            fun decode(value: String) = String(
                Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP),
                StandardCharsets.UTF_8
            )
            val ssid = decode(parts[2])
            val pass = decode(parts[3])
            val security = parts[4]
            val token = parts[5]
            if (ssid.isBlank() || token.length < 20 || security !in setOf("OPEN", "WPA2", "WPA3")) null
            else JoinInfo(ssid, pass, security, token)
        } catch (_: Exception) {
            null
        }
    }

    fun joinFromQr(payload: String) {
        cancelSession()
        closed = false
        isHost = false
        isJoining = true
        post { callbacks.onStatus("Checking the one-time QR…", false) }

        val info = parseQr(payload)
        if (info == null) {
            post { callbacks.onStatus("This is not a valid HADOC LocalDrop QR code.", false) }
            return
        }

        if (Build.VERSION.SDK_INT >= 29) {
            requestWifiNetwork(info)
        } else {
            joinLegacyWifi(info)
        }
    }

    private fun requestWifiNetwork(info: JoinInfo) {
        try {
            val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(info.ssid)
            when {
                info.security == "OPEN" || info.password.isBlank() -> Unit
                info.security == "WPA3" && Build.VERSION.SDK_INT >= 31 ->
                    specifierBuilder.setWpa3Passphrase(info.password)
                else -> specifierBuilder.setWpa2Passphrase(info.password)
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    post { callbacks.onStatus("Wi-Fi joined • connecting to LocalDrop…", false) }
                    io.execute { connectToHost(network, info.token) }
                }

                override fun onUnavailable() {
                    // Some Android builds can report the request as unavailable even
                    // though the selected local-only Wi-Fi network is already connected.
                    // Check the active Wi-Fi networks for the exact QR SSID before failing.
                    val matchingNetwork = connectivity.allNetworks.firstOrNull { candidate ->
                        val caps = connectivity.getNetworkCapabilities(candidate)
                        val wifiInfo = caps?.transportInfo as? android.net.wifi.WifiInfo
                        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                            wifiInfo?.ssid?.trim('"') == info.ssid
                    }

                    if (matchingNetwork != null) {
                        post { callbacks.onStatus("Hotspot connected • opening LocalDrop…", false) }
                        io.execute { connectToHost(matchingNetwork, info.token) }
                    } else {
                        post { callbacks.onStatus("Android could not confirm the temporary Wi-Fi connection. Keep the hotspot connected and scan the QR again.", false) }
                        unregisterWifiCallback()
                    }
                }

                override fun onLost(network: Network) {
                    if (activeSocket == null) {
                        post { callbacks.onStatus("The temporary hotspot was lost. Try scanning again.", false) }
                    }
                }
            }
            wifiNetworkCallback = callback
            connectivity.requestNetwork(request, callback, 45_000)
        } catch (error: Exception) {
            unregisterWifiCallback()
            post { callbacks.onStatus("Could not request Wi-Fi connection: " + (error.message ?: "error"), false) }
        }
    }

    @Suppress("DEPRECATION")
    private fun joinLegacyWifi(info: JoinInfo) {
        try {
            val config = WifiConfiguration().apply {
                SSID = "\"" + info.ssid + "\""
                if (info.security == "OPEN" || info.password.isBlank()) {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                } else {
                    preSharedKey = "\"" + info.password + "\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                }
            }
            val networkId = wifiManager.addNetwork(config)
            if (networkId < 0) throw IOException("Android could not add this Wi-Fi network")
            wifiManager.disconnect()
            if (!wifiManager.enableNetwork(networkId, true)) throw IOException("Could not enable hotspot network")
            wifiManager.reconnect()

            post { callbacks.onStatus("Joining " + info.ssid + "…", false) }
            io.execute {
                var connectedNetwork: Network? = null
                repeat(40) {
                    if (connectedNetwork == null) {
                        val active = connectivity.activeNetwork
                        val capabilities = active?.let { connectivity.getNetworkCapabilities(it) }
                        if (active != null && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                            connectedNetwork = active
                        }
                    }
                    if (connectedNetwork == null) Thread.sleep(500)
                }
                val network = connectedNetwork
                if (network == null) {
                    post { callbacks.onStatus("Could not join the hotspot. Open Wi-Fi settings and try again.", false) }
                } else {
                    connectToHost(network, info.token)
                }
            }
        } catch (error: Exception) {
            post { callbacks.onStatus("Could not join Wi-Fi: " + (error.message ?: "error"), false) }
        }
    }

    private fun connectToHost(network: Network, token: String) {
        var socket: Socket? = null
        try {
            val linkProperties = connectivity.getLinkProperties(network)
                ?: throw IOException("Wi-Fi route is not ready")
            val gateway = linkProperties.routes.firstOrNull { it.isDefaultRoute }?.gateway
                ?: throw IOException("Could not find the hotspot phone on this Wi-Fi network")

            socket = network.socketFactory.createSocket()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(gateway, TCP_PORT), 10_000)
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val stream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            val encodedName = Base64.encodeToString(
                deviceName.toByteArray(StandardCharsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
            stream.writeUTF("JOIN|" + token + "|" + encodedName)
            stream.flush()

            socket.soTimeout = 10_000
            val response = input.readUTF()
            val parts = response.split("|", limit = 2)
            if (parts.size != 2 || parts[0] != "READY") {
                throw IOException("The one-time QR has expired or was already used")
            }
            socket.soTimeout = 0
            val remoteName = String(
                Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP),
                StandardCharsets.UTF_8
            )
            installConnection(socket, input, stream, remoteName)
            socket = null
        } catch (error: Exception) {
            post { callbacks.onStatus("Could not connect: " + (error.message ?: "try scanning again"), false) }
            unregisterWifiCallback()
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun acceptSinglePhone(server: ServerSocket, token: String) {
        while (!closed && isHost && !tokenConsumed) {
            var socket: Socket? = null
            try {
                socket = server.accept()
                socket.soTimeout = 12_000
                socket.tcpNoDelay = true
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val stream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val request = input.readUTF()
                val parts = request.split("|", limit = 3)
                if (parts.size != 3 || parts[0] != "JOIN" || parts[1] != token || tokenConsumed) {
                    socket.close()
                    socket = null
                    continue
                }

                val remoteName = String(
                    Base64.decode(parts[2], Base64.URL_SAFE or Base64.NO_WRAP),
                    StandardCharsets.UTF_8
                )
                val claimToken = synchronized(sessionLock) {
                    if (tokenConsumed || closed) {
                        false
                    } else {
                        tokenConsumed = true
                        true
                    }
                }
                if (!claimToken) {
                    socket.close()
                    socket = null
                    continue
                }

                val encodedName = Base64.encodeToString(
                    deviceName.toByteArray(StandardCharsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP
                )
                stream.writeUTF("READY|" + encodedName)
                stream.flush()
                socket.soTimeout = 0
                installConnection(socket, input, stream, remoteName)
                socket = null
                break
            } catch (_: Exception) {
                try { socket?.close() } catch (_: Exception) {}
                if (closed || tokenConsumed) break
            }
        }
    }

    private fun installConnection(
        socket: Socket,
        input: DataInputStream,
        stream: DataOutputStream,
        remoteName: String
    ) {
        synchronized(sessionLock) {
            if (activeSocket?.isConnected == true) {
                socket.close()
                return
            }
            activeSocket = socket
            output = stream
        }
        post {
            callbacks.onPeerConnected(remoteName)
            callbacks.onStatus("Connected • " + remoteName, true)
        }
        io.execute { readLoop(socket, input) }
    }


    fun connectToPc(qrUrl: String) {
        val uri = try { Uri.parse(qrUrl.trim()) } catch (_: Exception) { null }
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host
        val token = uri?.getQueryParameter("token")
        if (uri == null || scheme !in setOf("http", "https") || host.isNullOrBlank() ||
            token.isNullOrBlank() || token.length < 24) {
            post { callbacks.onStatus("This is not a valid HADOC LocalDrop PC QR. Scan the QR shown in the Windows app.", false) }
            return
        }

        val baseUrl = "$scheme://${uri.encodedAuthority}"
        cancelSession()
        closed = false
        isHost = false
        isJoining = true
        pcMode = true
        pcBaseUrl = baseUrl
        pcToken = token
        pcDownloadedFileIds.clear()
        post { callbacks.onStatus("Contacting HADOC LocalDrop on your PC…", false) }

        io.execute {
            try {
                val connectRequest = Request.Builder()
                    .url("$baseUrl/api/connect")
                    .header("X-LocalDrop-Token", token)
                    .header("X-Device-Id", deviceId)
                    .header("X-Device-Name", deviceName)
                    .post(ByteArray(0).toRequestBody(null))
                    .build()

                val remotePcName = httpClient.newCall(connectRequest).execute().use { response ->
                    val responseText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = try { JSONObject(responseText).optString("error") } catch (_: Exception) { "" }
                        throw IOException(detail.ifBlank { "PC rejected the connection (HTTP ${response.code})" })
                    }
                    JSONObject(responseText).optString("pcName", "Your PC")
                }
                pcName = remotePcName
                if (!pcMode) return@execute

                val wsBase = when (scheme) {
                    "https" -> "wss://${uri.encodedAuthority}"
                    else -> "ws://${uri.encodedAuthority}"
                }
                val socketRequest = Request.Builder()
                    .url("$wsBase/ws?token=${Uri.encode(token)}")
                    .build()

                pcWebSocket = httpClient.newWebSocket(socketRequest, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (!pcMode) {
                            webSocket.close(1000, "Session ended")
                            return
                        }
                        isJoining = false
                        post {
                            callbacks.onPeerConnected(pcName)
                            callbacks.onStatus("Connected • $pcName", true)
                        }
                        startPcFilePolling()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val message = JSONObject(text)
                            val body = message.optString("text")
                            if (body.isNotBlank()) post { callbacks.onTextReceived(body) }
                        } catch (_: Exception) { }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (pcMode) {
                            post {
                                callbacks.onStatus("PC connection closed. Scan the PC QR again to reconnect.", false)
                                cancelSession()
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                        if (pcMode) {
                            val detail = error.message ?: "WebSocket connection failed"
                            post {
                                callbacks.onStatus("PC live connection failed: $detail", false)
                                cancelSession()
                            }
                        }
                    }
                })
            } catch (error: Exception) {
                if (pcMode) {
                    val detail = error.message ?: "Could not reach the PC"
                    post {
                        callbacks.onStatus("Could not connect to PC: $detail", false)
                        cancelSession()
                    }
                }
            }
        }
    }

    private fun pcRequest(path: String): Request.Builder {
        val base = pcBaseUrl ?: throw IOException("PC session is not active")
        val token = pcToken ?: throw IOException("PC pairing token is missing")
        return Request.Builder()
            .url(base + path)
            .header("X-LocalDrop-Token", token)
    }

    private fun startPcFilePolling() {
        pcPollTask?.let { mainHandler.removeCallbacks(it) }
        val task = object : Runnable {
            override fun run() {
                if (!pcMode) return
                io.execute { refreshPcFiles() }
                mainHandler.postDelayed(this, 3500L)
            }
        }
        pcPollTask = task
        mainHandler.post(task)
    }

    private fun refreshPcFiles() {
        if (!pcMode) return
        try {
            val request = pcRequest("/api/files").get().build()
            val items = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("PC file list failed (HTTP ${response.code})")
                JSONArray(response.body?.string().orEmpty())
            }
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name", "Shared file")
                val size = item.optLong("size", 0L)
                if (id.isNotBlank() && pcDownloadedFileIds.add(id)) {
                    io.execute { downloadPcFile(id, name, size) }
                }
            }
        } catch (error: Exception) {
            if (pcMode) post { callbacks.onStatus("PC file sync: ${error.message ?: "retrying"}", true) }
        }
    }

    private fun pcUiId(id: String): Long = id.hashCode().toLong() and Long.MAX_VALUE

    private fun downloadPcFile(fileId: String, fileName: String, size: Long) {
        val uiId = pcUiId(fileId)
        val safeName = File(fileName).name.ifBlank { "received_file" }
        val folder = File(appContext.filesDir, "received").apply { mkdirs() }
        val destination = File(folder, uiId.toString() + "_" + safeName)
        try {
            val request = pcRequest("/api/download/${Uri.encode(fileId)}").get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("PC download failed (HTTP ${response.code})")
                val body = response.body ?: throw IOException("PC sent an empty file")
                post { callbacks.onTransferCreated(uiId, safeName, size, false) }
                var loaded = 0L
                var lastUpdate = SystemClock.elapsedRealtime()
                body.byteStream().use { input ->
                    FileOutputStream(destination).use { outputStream ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            outputStream.write(buffer, 0, count)
                            loaded += count
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUpdate >= 250L || (size > 0 && loaded == size)) {
                                lastUpdate = now
                                val current = loaded
                                post { callbacks.onTransferProgress(uiId, false, current, size, "Receiving") }
                            }
                        }
                        outputStream.flush()
                    }
                }
                val actualSize = destination.length()
                post {
                    callbacks.onTransferProgress(uiId, false, actualSize, actualSize, "Complete")
                    callbacks.onFileReceived(uiId, destination.absolutePath, actualSize)
                }
            }
        } catch (error: Exception) {
            try { destination.delete() } catch (_: Exception) { }
            pcDownloadedFileIds.remove(fileId)
            if (pcMode) post { callbacks.onStatus("Could not download $safeName: ${error.message ?: "network error"}", true) }
        }
    }

    private fun reportPcTransfer(
        id: String,
        name: String,
        loaded: Long,
        total: Long,
        speed: Double,
        state: String
    ) {
        if (!pcMode) return
        try {
            val payload = JSONObject()
                .put("id", id)
                .put("direction", "phone-to-pc")
                .put("name", name)
                .put("loaded", loaded)
                .put("total", total)
                .put("bytesPerSecond", speed)
                .put("state", state)
                .toString()
            val request = pcRequest("/api/transfer/progress")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: okhttp3.Call, error: IOException) { }
                override fun onResponse(call: okhttp3.Call, response: Response) { response.close() }
            })
        } catch (_: Exception) { }
    }

    private fun sendFileToPc(uri: Uri) {
        io.execute {
            var staged: File? = null
            try {
                val name = queryDisplayName(uri)
                val folder = File(appContext.cacheDir, "outgoing").apply { mkdirs() }
                val file = File.createTempFile("localdrop_pc_", ".upload", folder)
                staged = file
                val source = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot read selected file")
                source.use { input ->
                    FileOutputStream(file).use { outputStream ->
                        input.copyTo(outputStream, 64 * 1024)
                    }
                }

                val size = file.length()
                val transferId = UUID.randomUUID().toString()
                val uiId = pcUiId(transferId)
                post { callbacks.onTransferCreated(uiId, name, size, true) }
                var loaded = 0L
                var lastUpdate = SystemClock.elapsedRealtime()
                var lastLoaded = 0L
                val body = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaType()
                    override fun contentLength() = file.length()
                    override fun writeTo(sink: BufferedSink) {
                        FileInputStream(file).use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                sink.write(buffer, 0, count)
                                loaded += count
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastUpdate >= 250L || loaded == size) {
                                    val elapsed = maxOf(1L, now - lastUpdate)
                                    val speed = (loaded - lastLoaded) * 1000.0 / elapsed
                                    lastUpdate = now
                                    lastLoaded = loaded
                                    val current = loaded
                                    post { callbacks.onTransferProgress(uiId, true, current, size, "Sending") }
                                    reportPcTransfer(transferId, name, current, size, speed, "running")
                                }
                            }
                        }
                    }
                }
                val request = pcRequest(
                    "/api/upload?name=${Uri.encode(name)}&transferId=${Uri.encode(transferId)}"
                ).post(body).build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = response.body?.string().orEmpty()
                        throw IOException(detail.ifBlank { "PC upload failed (HTTP ${response.code})" })
                    }
                }
                reportPcTransfer(transferId, name, size, size, 0.0, "completed")
                post { callbacks.onTransferProgress(uiId, true, size, size, "Complete") }
            } catch (error: Exception) {
                val message = error.message ?: "upload failed"
                post { callbacks.onStatus("PC file transfer failed: $message", true) }
            } finally {
                try { staged?.delete() } catch (_: Exception) { }
            }
        }
    }

    fun sendText(text: String) {
        if (pcMode) {
            val payload = JSONObject().put("text", text).toString()
            if (pcWebSocket?.send(payload) != true) {
                post { callbacks.onStatus("PC chat is reconnecting. Please try again.", true) }
            }
            return
        }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > 4 * 1024 * 1024) {
            post { callbacks.onStatus("Message is too large", true) }
            return
        }
        try {
            synchronized(outputLock) {
                val stream = output ?: throw IOException("Phone is not connected")
                stream.writeByte(1)
                stream.writeInt(bytes.size)
                stream.write(bytes)
                stream.flush()
            }
        } catch (error: Exception) {
            post { callbacks.onStatus("Message could not be sent: " + (error.message ?: "error"), false) }
        }
    }

    fun sendFile(uri: Uri) {
        if (pcMode) {
            sendFileToPc(uri)
            return
        }
        if (activeSocket?.isConnected != true) {
            post { callbacks.onStatus("Connect the other phone before sending files", false) }
            return
        }
        io.execute {
            var temp: File? = null
            try {
                val name = queryDisplayName(uri)
                val folder = File(appContext.cacheDir, "outgoing").apply { mkdirs() }
                val staged = File.createTempFile("localdrop_", ".upload", folder)
                temp = staged
                val source = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot read selected file")
                source.use { input ->
                    FileOutputStream(staged).use { output ->
                        input.copyTo(output, 64 * 1024)
                    }
                }

                val size = staged.length()
                val id = transferIds.incrementAndGet().and(Long.MAX_VALUE)
                post { callbacks.onTransferCreated(id, name, size, true) }
                var sent = 0L
                var lastUpdate = 0L

                synchronized(outputLock) {
                    val stream = output ?: throw IOException("Phone disconnected")
                    stream.writeByte(2)
                    stream.writeLong(id)
                    stream.writeUTF(name.take(500))
                    stream.writeLong(size)
                    stream.flush()
                    FileInputStream(staged).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            stream.write(buffer, 0, count)
                            sent += count
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUpdate >= 250L || sent == size) {
                                stream.flush()
                                lastUpdate = now
                                val progress = sent
                                post {
                                    callbacks.onTransferProgress(id, true, progress, size, "Sending")
                                }
                            }
                        }
                    }
                    stream.flush()
                }
                post { callbacks.onTransferProgress(id, true, size, size, "Complete") }
            } catch (error: Exception) {
                post { callbacks.onStatus("File transfer failed: " + (error.message ?: "connection lost"), false) }
            } finally {
                try { temp?.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Shared file"
        var cursor: android.database.Cursor? = null
        try {
            cursor = appContext.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return File(name).name.ifBlank { "Shared file" }
    }

    private fun readLoop(socket: Socket, input: DataInputStream) {
        try {
            while (!closed && socket.isConnected && !socket.isClosed) {
                when (input.readUnsignedByte()) {
                    1 -> {
                        val length = input.readInt()
                        if (length < 0 || length > 4 * 1024 * 1024) throw IOException("Invalid message size")
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        val text = String(bytes, StandardCharsets.UTF_8)
                        post { callbacks.onTextReceived(text) }
                    }
                    2 -> receiveFile(input)
                    else -> throw IOException("Unknown LocalDrop packet")
                }
            }
        } catch (_: Exception) {
        } finally {
            val wasCurrent = synchronized(sessionLock) {
                if (activeSocket === socket) {
                    activeSocket = null
                    output = null
                    true
                } else false
            }
            try { socket.close() } catch (_: Exception) {}
            if (wasCurrent && !closed) {
                cancelSession()
                post { callbacks.onStatus("Phone disconnected. Scan or show a new QR to reconnect.", false) }
            }
        }
    }

    private fun receiveFile(input: DataInputStream) {
        val id = input.readLong()
        val name = File(input.readUTF()).name.ifBlank { "received_file" }
        val total = input.readLong()
        if (total < 0L) throw IOException("Invalid file size")

        val folder = File(appContext.filesDir, "received").apply { mkdirs() }
        val destination = File(folder, id.toString() + "_" + name)
        post { callbacks.onTransferCreated(id, name, total, false) }

        var received = 0L
        var lastUpdate = 0L
        FileOutputStream(destination).use { fileOutput ->
            val buffer = ByteArray(64 * 1024)
            while (received < total) {
                val wanted = minOf(buffer.size.toLong(), total - received).toInt()
                val count = input.read(buffer, 0, wanted)
                if (count < 0) throw IOException("Connection ended during file transfer")
                fileOutput.write(buffer, 0, count)
                received += count
                val now = SystemClock.elapsedRealtime()
                if (now - lastUpdate >= 250L || received == total) {
                    lastUpdate = now
                    val progress = received
                    post { callbacks.onTransferProgress(id, false, progress, total, "Receiving") }
                }
            }
            fileOutput.flush()
        }

        post {
            callbacks.onTransferProgress(id, false, total, total, "Complete")
            callbacks.onFileReceived(id, destination.absolutePath, destination.length())
        }
    }

    fun cancelSession() {
        val wasPcMode = pcMode
        val oldPcBase = pcBaseUrl
        val oldPcToken = pcToken
        pcMode = false
        pcBaseUrl = null
        pcToken = null
        pcPollTask?.let { mainHandler.removeCallbacks(it) }
        pcPollTask = null
        try { pcWebSocket?.close(1000, "Disconnected") } catch (_: Exception) { }
        pcWebSocket = null
        pcDownloadedFileIds.clear()
        if (wasPcMode && oldPcBase != null && !oldPcToken.isNullOrBlank()) {
            io.execute {
                try {
                    val request = Request.Builder()
                        .url("$oldPcBase/api/disconnect")
                        .header("X-LocalDrop-Token", oldPcToken)
                        .post(ByteArray(0).toRequestBody(null))
                        .build()
                    httpClient.newCall(request).execute().close()
                } catch (_: Exception) { }
            }
        }
        try { activeSocket?.close() } catch (_: Exception) { }
        activeSocket = null
        output = null
        try { hostServer?.close() } catch (_: Exception) {}
        hostServer = null
        try { hotspotReservation?.close() } catch (_: Exception) {}
        hotspotReservation = null
        unregisterWifiCallback()
        hostToken = null
        tokenConsumed = false
        isHost = false
        isJoining = false
    }

    private fun unregisterWifiCallback() {
        val callback = wifiNetworkCallback
        wifiNetworkCallback = null
        if (callback != null) {
            try { connectivity.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        }
    }

    fun close() {
        closed = true
        cancelSession()
        io.shutdownNow()
    }
}