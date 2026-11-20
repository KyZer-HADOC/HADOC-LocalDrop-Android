package com.kyzerhadoc.localdrop

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import android.database.Cursor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

private const val SERVICE_ID = "com.kyzerhadoc.localdrop.mobile.v1"
private val Background = Color(0xFF090A0F)
private val Panel = Color(0xFF151821)
private val Crimson = Color(0xFFE53950)
private val Muted = Color(0xFF9AA3B2)
private val TextMain = Color(0xFFF4F5F8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 10, 15)
        window.navigationBarColor = android.graphics.Color.rgb(9, 10, 15)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Crimson,
                secondary = Color(0xFFFF647A),
                background = Background,
                surface = Panel,
                onPrimary = Color.White,
                onBackground = TextMain,
                onSurface = TextMain
            )) {
                LocalDropScreen()
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
private fun LocalDropScreen() {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<ChatEntry>() }
    val transfers = remember { mutableStateMapOf<Long, TransferUi>() }
    var connectionStatus by remember { mutableStateOf("Searching for nearby phones…") }
    var connected by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val engine = remember {
        NearbyEngine(context, object : NearbyEngine.Callbacks {
            override fun onStatus(message: String, isConnected: Boolean) {
                connectionStatus = message
                connected = isConnected
            }

            override fun onTextReceived(text: String) {
                messages.add(ChatEntry(
                    id = System.nanoTime(),
                    text = text,
                    mine = false
                ))
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
                status: String
            ) {
                val old = transfers[id]
                transfers[id] = TransferUi(
                    name = old?.name ?: "Receiving file…",
                    mine = mine,
                    total = if (total > 0) total else (old?.total ?: -1L),
                    transferred = transferred,
                    status = status,
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

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            if (!connected) {
                Toast.makeText(context, "Connect another phone first", Toast.LENGTH_SHORT).show()
            } else {
                uris.forEach { engine.sendFile(it) }
            }
        }
    }

    val requiredPermissions = remember { requiredNearbyPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            engine.start()
        } else {
            connectionStatus = "Nearby permissions are required to connect"
            connected = false
        }
    }

    LaunchedEffect(Unit) {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            engine.start()
        } else {
            connectionStatus = "Allow Nearby devices to start auto-connect"
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    DisposableEffect(engine) {
        onDispose { engine.stop() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10131B))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Crimson, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("H", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text("HADOC LocalDrop", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("MOBILE  ↔  MOBILE", fontSize = 10.sp, color = Muted, letterSpacing = 1.6.sp)
            }
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = if (connected) Color(0xFF47D18C) else Crimson,
                modifier = Modifier.size(24.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (connected) Color(0xFF10271F) else Color(0xFF25151B))
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (connected) {
                Box(Modifier.size(8.dp).background(Color(0xFF47D18C), CircleShape))
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = Crimson
                )
            }
            Text(
                text = connectionStatus,
                color = if (connected) Color(0xFF8DE0B4) else Color(0xFFFFA4AF),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 10.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(if (connected) "ONLINE" else "AUTO SEARCH", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
        }

        HorizontalDivider(color = Color(0xFF222733), thickness = 1.dp)

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(82.dp)
                            .background(Color(0xFF21151D), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = Crimson, modifier = Modifier.size(38.dp))
                    }
                    Text(
                        "Your local space",
                        modifier = Modifier.padding(top = 18.dp),
                        color = TextMain,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (connected) "Phone connected. Send a message or add files."
                        else "Open HADOC LocalDrop on the second phone nearby.",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "No cloud • No mobile data • Local transfer",
                        color = Color(0xFF687284),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 15.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { entry ->
                    val transfer = entry.transferId?.let { transfers[it] }
                    MessageBubble(
                        entry = entry,
                        transfer = transfer,
                        onOpenFile = { path -> openReceivedFile(context, path) }
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF222733), thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10131B))
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = connected,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF29202A), RoundedCornerShape(15.dp))
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Add files", tint = if (connected) Crimson else Muted)
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                placeholder = { Text("Message…", color = Muted, fontSize = 14.sp) },
                minLines = 1,
                maxLines = 4,
                enabled = connected,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            IconButton(
                onClick = {
                    val value = draft.trimEnd()
                    if (value.isNotBlank() && connected) {
                        messages.add(ChatEntry(
                            id = System.nanoTime(),
                            text = value,
                            mine = true
                        ))
                        engine.sendText(value)
                        draft = ""
                    }
                },
                enabled = connected && draft.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(if (connected && draft.isNotBlank()) Crimson else Color(0xFF3A3038), RoundedCornerShape(15.dp))
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
        Text(
            "PRIVATE PEER-TO-PEER CONNECTION",
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF10131B))
                .padding(bottom = 8.dp),
            color = Color(0xFF687284),
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
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
            colors = CardDefaults.cardColors(
                containerColor = if (entry.mine) Color(0xFF54202C) else Panel
            )
        ) {
            Column(Modifier.padding(13.dp)) {
                if (entry.text != null) {
                    Text(
                        entry.text,
                        color = TextMain,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = Color(0xFFFF8393))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                entry.fileName ?: "File",
                                color = TextMain,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                if (entry.fileSize > 0) formatBytes(entry.fileSize) else "File transfer",
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                        if (entry.filePath != null) {
                            Button(
                                onClick = { onOpenFile(entry.filePath) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text("Open", fontSize = 11.sp)
                            }
                        }
                    }
                    if (transfer != null) {
                        val fraction = if (transfer.total > 0) {
                            (transfer.transferred.toFloat() / transfer.total.toFloat()).coerceIn(0f, 1f)
                        } else null
                        LinearProgressIndicator(
                            progress = { fraction ?: 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 11.dp),
                            color = Crimson,
                            trackColor = Color(0xFF3A3540)
                        )
                        val elapsed = (System.currentTimeMillis() - transfer.startedAt).coerceAtLeast(1L)
                        val speed = transfer.transferred * 1000L / elapsed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(transfer.status, color = Muted, fontSize = 10.sp)
                            Text(
                                if (fraction != null && transfer.status == "Sending") {
                                    formatBytes(speed) + "/s • " + (fraction * 100).toInt() + "%"
                                } else if (transfer.status == "Complete") {
                                    "Complete"
                                } else {
                                    formatBytes(speed) + "/s"
                                },
                                color = Color(0xFFFFA0AD),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun requiredNearbyPermissions(): Array<String> {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= 33) {
        permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else if (Build.VERSION.SDK_INT >= 31) {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
        permissions += Manifest.permission.BLUETOOTH_CONNECT
    } else if (Build.VERSION.SDK_INT >= 29) {
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
    } else {
        permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        permissions += Manifest.permission.BLUETOOTH
        permissions += Manifest.permission.BLUETOOTH_ADMIN
    }
    return permissions.distinct().toTypedArray()
}

private fun openReceivedFile(context: Context, path: String) {
    try {
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, "com.kyzerhadoc.localdrop.files", file)
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(view, "Open received file"))
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

private class NearbyEngine(
    context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onStatus(message: String, isConnected: Boolean)
        fun onTextReceived(text: String)
        fun onTransferCreated(id: Long, name: String, size: Long, mine: Boolean)
        fun onTransferProgress(id: Long, mine: Boolean, transferred: Long, total: Long, status: String)
        fun onFileReceived(id: Long, path: String, size: Long)
    }

    private val appContext = context.applicationContext
    private val client: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val endpoints = ConcurrentHashMap.newKeySet<String>()
    private val requested = ConcurrentHashMap.newKeySet<String>()
    private val fileTransferIds = ConcurrentHashMap.newKeySet<Long>()
    private val outgoingIds = ConcurrentHashMap.newKeySet<Long>()
    private val receivedFiles = ConcurrentHashMap<Long, Payload>()
    private val completedFiles = ConcurrentHashMap<Long, Payload>()
    private val receivedNames = ConcurrentHashMap<Long, String>()
    private val outgoingDescriptors = ConcurrentHashMap<Long, android.os.ParcelFileDescriptor>()
    @Volatile private var started = false

    private fun post(action: () -> Unit) {
        mainHandler.post(action)
    }

    fun start() {
        if (started) return
        started = true
        post { callbacks.onStatus("Searching for nearby phones…", false) }
        val advertising = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()
        val discovery = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        client.startAdvertising(
            Build.MODEL ?: "Android",
            SERVICE_ID,
            lifecycleCallback,
            advertising
        ).addOnFailureListener { error ->
            post { callbacks.onStatus("Advertising failed: " + (error.message ?: "check permissions"), false) }
        }

        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            discovery
        ).addOnFailureListener { error ->
            post { callbacks.onStatus("Discovery failed: " + (error.message ?: "check Wi-Fi/Bluetooth"), false) }
        }
    }

    fun stop() {
        started = false
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        endpoints.clear()
        requested.clear()
        io.shutdown()
    }

    fun sendText(text: String) {
        val endpoint = endpoints.firstOrNull() ?: return
        val payload = Payload.fromBytes(("TEXT|" + text).toByteArray(StandardCharsets.UTF_8))
        client.sendPayload(endpoint, payload)
    }

    fun sendFile(uri: Uri) {
        val endpoint = endpoints.firstOrNull()
        if (endpoint == null) {
            post { callbacks.onStatus("Connect another phone before sending files", false) }
            return
        }
        io.execute {
            var descriptor: android.os.ParcelFileDescriptor? = null
            try {
                descriptor = appContext.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalStateException("Cannot read the selected file")
                val name = queryDisplayName(uri)
                val size = descriptor.statSize
                val payload = Payload.fromFile(descriptor)
                val id = payload.id
                outgoingIds.add(id)
                fileTransferIds.add(id)
                outgoingDescriptors[id] = descriptor
                descriptor = null
                val encodedName = Base64.encodeToString(name.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                val metadata = "META|" + id + "|" + size + "|" + encodedName
                post { callbacks.onTransferCreated(id, name, size, true) }
                client.sendPayload(
                    endpoint,
                    Payload.fromBytes(metadata.toByteArray(StandardCharsets.UTF_8))
                ).addOnFailureListener { error ->
                    post { callbacks.onStatus("File details could not be sent: " + (error.message ?: "error"), true) }
                }
                client.sendPayload(endpoint, payload).addOnFailureListener { error ->
                    post {
                        callbacks.onTransferProgress(id, true, 0L, size, "Failed")
                        callbacks.onStatus("File transfer failed: " + (error.message ?: "error"), true)
                    }
                    outgoingDescriptors.remove(id)?.close()
                }
            } catch (error: Exception) {
                descriptor?.close()
                post { callbacks.onStatus("Could not prepare file: " + (error.message ?: "error"), true) }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "Shared file"
        var cursor: Cursor? = null
        try {
            cursor = appContext.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index) ?: name
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return File(name).name.ifBlank { "Shared file" }
    }

    private val discoveryCallback: EndpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (endpoints.isEmpty() && requested.add(endpointId)) {
                post { callbacks.onStatus("Found " + info.endpointName + " • connecting…", false) }
                client.requestConnection(
                    Build.MODEL ?: "Android",
                    endpointId,
                    lifecycleCallback
                ).addOnFailureListener {
                    requested.remove(endpointId)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            requested.remove(endpointId)
        }
    }

    private val lifecycleCallback: ConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            post { callbacks.onStatus("Connecting to " + info.endpointName + "…", false) }
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { error ->
                    post { callbacks.onStatus("Connection rejected: " + (error.message ?: "error"), false) }
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                endpoints.add(endpointId)
                requested.add(endpointId)
                post { callbacks.onStatus("Connected • " + (Build.MODEL ?: "Android") + " ↔ phone", true) }
            } else {
                requested.remove(endpointId)
                post { callbacks.onStatus("Could not connect. Searching again…", false) }
            }
        }

        override fun onDisconnected(endpointId: String) {
            endpoints.remove(endpointId)
            requested.remove(endpointId)
            post {
                callbacks.onStatus("Phone disconnected • searching again…", false)
            }
            if (started) {
                client.startAdvertising(
                    Build.MODEL ?: "Android",
                    SERVICE_ID,
                    lifecycleCallback,
                    AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
                )
                client.startDiscovery(
                    SERVICE_ID,
                    discoveryCallback,
                    DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
                )
            }
        }
    }

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val value = String(payload.asBytes() ?: ByteArray(0), StandardCharsets.UTF_8)
                    if (value.startsWith("TEXT|")) {
                        post { callbacks.onTextReceived(value.removePrefix("TEXT|")) }
                    } else if (value.startsWith("META|")) {
                        val parts = value.split("|", limit = 4)
                        if (parts.size == 4) {
                            val id = parts[1].toLongOrNull()
                            if (id != null) {
                                val name = try {
                                    String(Base64.decode(parts[3], Base64.NO_WRAP), StandardCharsets.UTF_8)
                                } catch (_: Exception) {
                                    "Shared file"
                                }
                                val size = parts[2].toLongOrNull() ?: -1L
                                receivedNames[id] = File(name).name
                                fileTransferIds.add(id)
                                post { callbacks.onTransferCreated(id, File(name).name, size, false) }
                                processCompletedFile(id)
                            }
                        }
                    }
                }
                Payload.Type.FILE -> {
                    receivedFiles[payload.id] = payload
                    fileTransferIds.add(payload.id)
                    post {
                        callbacks.onTransferCreated(
                            payload.id,
                            receivedNames[payload.id] ?: "Incoming file…",
                            -1L,
                            false
                        )
                    }
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val id = update.payloadId
            if (!fileTransferIds.contains(id)) return
            val mine = outgoingIds.contains(id)
            val status = when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> if (mine) "Sending" else "Receiving"
                PayloadTransferUpdate.Status.SUCCESS -> "Complete"
                PayloadTransferUpdate.Status.FAILURE -> "Failed"
                PayloadTransferUpdate.Status.CANCELED -> "Canceled"
                else -> "Working"
            }
            post {
                callbacks.onTransferProgress(
                    id,
                    mine,
                    update.bytesTransferred,
                    update.totalBytes,
                    status
                )
            }

            if (update.status == PayloadTransferUpdate.Status.SUCCESS && !mine) {
                receivedFiles[id]?.let { completedFiles[id] = it }
                processCompletedFile(id)
            }

            if (update.status == PayloadTransferUpdate.Status.SUCCESS ||
                update.status == PayloadTransferUpdate.Status.FAILURE ||
                update.status == PayloadTransferUpdate.Status.CANCELED
            ) {
                outgoingDescriptors.remove(id)?.close()
            }
        }
    }

    private fun processCompletedFile(id: Long) {
        val payload = completedFiles[id] ?: return
        val name = receivedNames[id] ?: return
        if (!completedFiles.remove(id, payload)) return
        receivedFiles.remove(id)
        io.execute {
            try {
                val receivedFile = payload.asFile()
                    ?: throw IllegalStateException("Received file payload is unavailable")
                val sourceUri = receivedFile.asUri()
                    ?: throw IllegalStateException("Received file URI is unavailable")
                val folder = File(appContext.filesDir, "received")
                folder.mkdirs()
                val safeName = File(name).name.ifBlank { "received_file" }
                var destination = File(folder, safeName)
                if (destination.exists()) {
                    destination = File(folder, System.currentTimeMillis().toString() + "_" + safeName)
                }
                val input = appContext.contentResolver.openInputStream(sourceUri)
                    ?: throw IllegalStateException("Received file could not be opened")
                input.use { stream ->
                    FileOutputStream(destination).use { output ->
                        stream.copyTo(output, 64 * 1024)
                    }
                }
                appContext.contentResolver.delete(sourceUri, null, null)
                receivedNames.remove(id)
                post { callbacks.onFileReceived(id, destination.absolutePath, destination.length()) }
            } catch (error: Exception) {
                post {
                    callbacks.onTransferProgress(id, false, 0L, -1L, "Failed")
                    callbacks.onStatus("Could not save received file: " + (error.message ?: "error"), true)
                }
            }
        }
    }
}
