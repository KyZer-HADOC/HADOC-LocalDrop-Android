package com.kyzerhadoc.localdrop

import android.content.Context
import android.content.Intent
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
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import android.database.Cursor
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap

private const val TCP_PORT = 46789
private const val DISCOVERY_PORT = 46790
private const val PROTOCOL_PREFIX = "HADOC_LOCALDROP_WIFI_V1"
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
    var connectionStatus by remember { mutableStateOf("Finding phones on this Wi-Fi / hotspot…") }
    var connected by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val engine = remember {
        LocalLanEngine(context, object : LocalLanEngine.Callbacks {
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

    LaunchedEffect(Unit) {
        engine.start()
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
            Image(
                painter = painterResource(id = R.drawable.ic_localdrop),
                contentDescription = "HADOC LocalDrop",
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
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
                        else "Open LocalDrop on a second phone connected to the same Wi-Fi or hotspot.",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "Wi-Fi / Hotspot only  •  No Bluetooth  •  No cloud",
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

private class LocalLanEngine(
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
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val preferences = appContext.getSharedPreferences("localdrop_lan", Context.MODE_PRIVATE)
    private val localId = preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("device_id", it).apply()
    }
    private val localName = (Build.MODEL ?: "Android phone").take(40)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val started = AtomicBoolean(false)
    private val connecting = ConcurrentHashMap.newKeySet<String>()
    private val connectionLock = Any()
    private val outputLock = Any()
    private val nextTransferId = java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() shl 10)

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var discoverySocket: DatagramSocket? = null
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var output: DataOutputStream? = null
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null

    private fun post(action: () -> Unit) {
        mainHandler.post(action)
    }

    private fun wifiNetwork(): Network? {
        val active = connectivity.activeNetwork
        if (active != null &&
            connectivity.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        ) return active
        return connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    private fun isWifiInterface(name: String): Boolean {
        val value = name.lowercase()
        return value.startsWith("wlan") ||
            value.startsWith("swlan") ||
            value.startsWith("ap") ||
            value.startsWith("wifi")
    }

    private fun wifiBroadcastAddresses(): List<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback ||
                    !isWifiInterface(networkInterface.name)
                ) continue
                for (address in networkInterface.interfaceAddresses) {
                    val broadcast = address.broadcast
                    if (address.address is Inet4Address && broadcast != null) {
                        addresses.add(broadcast)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return addresses.toList()
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        try {
            multicastLock = wifiManager.createMulticastLock("HADOC-LocalDrop-WiFi").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }

        post {
            callbacks.onStatus(
                if (wifiNetwork() != null || wifiBroadcastAddresses().isNotEmpty()) {
                    "Searching on local Wi-Fi / hotspot…"
                } else {
                    "Turn on Wi-Fi and join the same Wi-Fi or hotspot on both phones"
                },
                false
            )
        }

        io.execute { acceptLoop() }
        io.execute { discoveryLoop() }
        io.execute { broadcastLoop() }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        try { serverSocket?.close() } catch (_: Exception) {}
        try { discoverySocket?.close() } catch (_: Exception) {}
        try { activeSocket?.close() } catch (_: Exception) {}
        try { multicastLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        multicastLock = null
        output = null
        io.shutdownNow()
    }

    fun sendText(text: String) {
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
            onConnectionError(error)
        }
    }

    fun sendFile(uri: Uri) {
        if (activeSocket?.isConnected != true) {
            post { callbacks.onStatus("Connect another phone on the same Wi-Fi first", false) }
            return
        }

        io.execute {
            var temporaryFile: File? = null
            try {
                val name = queryDisplayName(uri)
                val outgoingFolder = File(appContext.cacheDir, "outgoing").apply { mkdirs() }
                val uploadFile = File.createTempFile("localdrop_", ".upload", outgoingFolder)
                temporaryFile = uploadFile

                val source = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot read the selected file")
                source.use { input ->
                    FileOutputStream(uploadFile).use { fileOutput ->
                        input.copyTo(fileOutput, 64 * 1024)
                    }
                }

                val size = uploadFile.length()
                val id = nextTransferId.incrementAndGet().and(Long.MAX_VALUE)
                post { callbacks.onTransferCreated(id, name, size, true) }

                var sent = 0L
                var lastUpdate = 0L
                synchronized(outputLock) {
                    val stream = output ?: throw IOException("Phone disconnected before transfer")
                    stream.writeByte(2)
                    stream.writeLong(id)
                    stream.writeUTF(name.take(500))
                    stream.writeLong(size)
                    stream.flush()

                    FileInputStream(uploadFile).use { input ->
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
                try { temporaryFile?.delete() } catch (_: Exception) {}
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

    private fun helloPacket(): ByteArray {
        val encodedName = Base64.encodeToString(
            localName.toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE
        )
        return "$PROTOCOL_PREFIX|$localId|$TCP_PORT|$encodedName".toByteArray(StandardCharsets.UTF_8)
    }

    private fun broadcastLoop() {
        while (started.get()) {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                wifiNetwork()?.let { network ->
                    try { network.bindSocket(socket) } catch (_: Exception) {}
                }
                val bytes = helloPacket()
                val destinations = wifiBroadcastAddresses().toMutableSet()
                try { destinations.add(InetAddress.getByName("255.255.255.255")) } catch (_: Exception) {}
                for (address in destinations) {
                    try {
                        socket.send(DatagramPacket(bytes, bytes.size, address, DISCOVERY_PORT))
                    } catch (_: Exception) {
                    }
                }
                socket.close()
            } catch (_: Exception) {
            }
            try { Thread.sleep(1800L) } catch (_: InterruptedException) { break }
        }
    }

    private fun discoveryLoop() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 1500
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
            wifiNetwork()?.let { network ->
                try { network.bindSocket(socket) } catch (_: Exception) {}
            }
            discoverySocket = socket

            val buffer = ByteArray(1024)
            while (started.get()) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                    val parts = message.split("|", limit = 4)
                    if (parts.size != 4 || parts[0] != PROTOCOL_PREFIX) continue

                    val remoteId = parts[1]
                    val remotePort = parts[2].toIntOrNull() ?: continue
                    if (remoteId == localId || remoteId.isBlank() || remotePort !in 1..65535) continue

                    val remoteName = try {
                        String(Base64.decode(parts[3], Base64.NO_WRAP or Base64.URL_SAFE), StandardCharsets.UTF_8)
                    } catch (_: Exception) {
                        "Nearby phone"
                    }

                    if (activeSocket?.isConnected == true) continue
                    if (localId < remoteId && connecting.add(remoteId)) {
                        post { callbacks.onStatus("Found $remoteName • connecting over Wi-Fi…", false) }
                        val address = packet.address
                        io.execute { connectTo(address, remotePort, remoteId, remoteName) }
                    }
                } catch (_: java.net.SocketTimeoutException) {
                } catch (_: Exception) {
                    if (started.get()) post {
                        callbacks.onStatus("Wi-Fi discovery retrying…", false)
                    }
                }
            }
        } catch (error: Exception) {
            if (started.get()) post {
                callbacks.onStatus("Cannot listen for Wi-Fi devices: " + (error.message ?: "network error"), false)
            }
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            discoverySocket = null
        }
    }

    private fun acceptLoop() {
        var server: ServerSocket? = null
        try {
            server = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(TCP_PORT), 8)
                soTimeout = 1500
            }
            serverSocket = server
            while (started.get()) {
                try {
                    val socket = server.accept()
                    io.execute { acceptPeer(socket) }
                } catch (_: java.net.SocketTimeoutException) {
                }
            }
        } catch (error: Exception) {
            if (started.get()) post {
                callbacks.onStatus("Cannot open local Wi-Fi port: " + (error.message ?: "port unavailable"), false)
            }
        } finally {
            try { server?.close() } catch (_: Exception) {}
            serverSocket = null
        }
    }

    private fun connectTo(address: InetAddress, port: Int, remoteId: String, remoteName: String) {
        var pendingSocket: Socket? = null
        try {
            val socket = Socket()
            pendingSocket = socket
            socket.tcpNoDelay = true
            wifiNetwork()?.let { network ->
                try { network.bindSocket(socket) } catch (_: Exception) {}
            }
            socket.connect(InetSocketAddress(address, port), 5000)

            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val stream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val encodedName = Base64.encodeToString(
                localName.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE
            )
            stream.writeUTF("CONNECT|$localId|$encodedName")
            stream.flush()

            val response = input.readUTF()
            val parts = response.split("|", limit = 3)
            if (parts.size != 3 || parts[0] != "WELCOME" || parts[1] != remoteId) {
                throw IOException("Phone identity handshake failed")
            }
            installConnection(socket, input, stream, remoteName)
            pendingSocket = null
        } catch (error: Exception) {
            connecting.remove(remoteId)
            if (started.get() && activeSocket?.isConnected != true) {
                post { callbacks.onStatus("Could not connect to $remoteName • retrying…", false) }
            }
        } finally {
            try { pendingSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun acceptPeer(socket: Socket) {
        try {
            socket.soTimeout = 6000
            socket.tcpNoDelay = true
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            val stream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val request = input.readUTF()
            val parts = request.split("|", limit = 3)
            if (parts.size != 3 || parts[0] != "CONNECT") throw IOException("Invalid LocalDrop connection")

            val remoteId = parts[1]
            if (remoteId >= localId) throw IOException("Connection direction is not valid")
            val remoteName = try {
                String(Base64.decode(parts[2], Base64.NO_WRAP or Base64.URL_SAFE), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                "Nearby phone"
            }

            val responseName = Base64.encodeToString(
                localName.toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP or Base64.URL_SAFE
            )
            stream.writeUTF("WELCOME|$localId|$responseName")
            stream.flush()
            socket.soTimeout = 0
            installConnection(socket, input, stream, remoteName)
        } catch (_: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun installConnection(
        socket: Socket,
        input: DataInputStream,
        stream: DataOutputStream,
        remoteName: String
    ) {
        synchronized(connectionLock) {
            if (activeSocket?.isConnected == true) {
                socket.close()
                return
            }
            activeSocket = socket
            output = stream
            connecting.clear()
        }
        post { callbacks.onStatus("Connected • $remoteName • Wi-Fi", true) }
        io.execute { readLoop(socket, input) }
    }

    private fun readLoop(socket: Socket, input: DataInputStream) {
        try {
            while (started.get() && socket.isConnected && !socket.isClosed) {
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
            synchronized(connectionLock) {
                if (activeSocket === socket) {
                    activeSocket = null
                    output = null
                    try { socket.close() } catch (_: Exception) {}
                    if (started.get()) post {
                        callbacks.onStatus("Phone disconnected • searching on Wi-Fi…", false)
                    }
                }
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
                    post {
                        callbacks.onTransferProgress(id, false, progress, total, "Receiving")
                    }
                }
            }
            fileOutput.flush()
        }

        post {
            callbacks.onTransferProgress(id, false, total, total, "Complete")
            callbacks.onFileReceived(id, destination.absolutePath, destination.length())
        }
    }

    private fun onConnectionError(error: Exception) {
        post {
            callbacks.onStatus("Connection lost: " + (error.message ?: "Wi-Fi disconnected"), false)
        }
    }
}
