package com.parth.airgap

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.parth.airgap.databinding.ActivityMainBinding
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * AirGap v2 — one shared offline chat room with application-level mesh relay.
 *
 * Every phone both advertises and discovers at the same time using
 * [Strategy.P2P_CLUSTER], so several phones form one many-to-many room.
 * No server, no account and no internet are involved: Nearby Connections
 * talks directly between phones over Bluetooth / local Wi-Fi radios.
 *
 * Wire protocol (v2): `MSG|<id>|<ttl>|<nickname>|<text>`
 *  - id: random UUID, identical across every relay hop of that message.
 *  - ttl: starts at [INITIAL_TTL] (3) and is decremented by each relay phone.
 *    A received message is forwarded only while its incoming ttl is > 1, so one
 *    message can cross at most 3 hops.
 *  - A per-device seen-set of ids drops duplicates and stops relay loops:
 *    a message coming back to a phone that already has its id is ignored.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var chatAdapter: ChatAdapter

    private var myNickname: String = ""
    private var running = false
    private var startRequested = false

    /** Endpoints we have seen / are talking to: endpointId -> nickname shown by that phone. */
    private val endpointNames = mutableMapOf<String, String>()
    private val connectingEndpoints = mutableSetOf<String>()
    private val connectedEndpoints = mutableSetOf<String>()

    /**
     * Ids of messages already seen (sent by me or received), in insertion order.
     * Bounded at [MAX_SEEN_IDS]; the eldest entries are evicted first.
     */
    private val seenMessageIds = LinkedHashSet<String>()

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        connectionsClient = Nearby.getConnectionsClient(this)

        chatAdapter = ChatAdapter()
        chatAdapter.viaRelaySuffix = getString(R.string.via_relay_suffix)
        binding.messagesList.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.messagesList.adapter = chatAdapter

        binding.startStopButton.setOnClickListener {
            if (running) stopRoom() else requestStart()
        }
        binding.sendButton.setOnClickListener { sendMessage() }
        binding.messageInput.setOnEditorActionListener { _, _, _ ->
            sendMessage()
            true
        }

        updateStatus("Not started")
        updatePeers()
    }

    override fun onDestroy() {
        if (running) stopRoom()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ start / stop

    /** Checks the nickname + runtime permissions, then starts the room. */
    private fun requestStart() {
        // '|' is the wire-protocol separator, so it can never appear in a nickname.
        val name = binding.nicknameInput.text.toString().replace("|", "").trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Pick a nickname first", Toast.LENGTH_SHORT).show()
            return
        }
        myNickname = name

        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            startRequested = true
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
            return
        }
        startRoom()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        val allGranted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (allGranted && startRequested) {
            startRoom()
        } else if (startRequested) {
            Toast.makeText(
                this,
                "AirGap needs Bluetooth / nearby permissions to find other phones",
                Toast.LENGTH_LONG
            ).show()
        }
        startRequested = false
    }

    private fun startRoom() {
        running = true
        binding.startStopButton.text = getString(R.string.stop)
        binding.nicknameInput.isEnabled = false

        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            myNickname, SERVICE_ID, connectionLifecycleCallback, advertisingOptions
        ).addOnSuccessListener {
            updateStatus("Live — advertising + discovering")
        }.addOnFailureListener { e ->
            updateStatus("Advertising failed: ${e.message}")
        }

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            SERVICE_ID, endpointDiscoveryCallback, discoveryOptions
        ).addOnFailureListener { e ->
            updateStatus("Discovery failed: ${e.message}")
        }

        addSystemMessage("Room started as \"$myNickname\". Waiting for other phones…")
    }

    private fun stopRoom() {
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
        connectingEndpoints.clear()
        endpointNames.clear()
        running = false
        binding.startStopButton.text = getString(R.string.start)
        binding.nicknameInput.isEnabled = true
        updateStatus("Stopped")
        updatePeers()
        addSystemMessage("You left the room")
    }

    // ------------------------------------------------------------------ discovery

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            endpointNames[endpointId] = info.endpointName
            updatePeers()
            // Auto-connect to every other AirGap phone we find (that isn't already handled).
            if (endpointId !in connectedEndpoints && endpointId !in connectingEndpoints) {
                connectingEndpoints.add(endpointId)
                updateStatus("Connecting to ${info.endpointName}…")
                connectionsClient.requestConnection(
                    myNickname, endpointId, connectionLifecycleCallback
                ).addOnFailureListener {
                    connectingEndpoints.remove(endpointId)
                    updatePeers()
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            if (endpointId !in connectedEndpoints) {
                endpointNames.remove(endpointId)
                connectingEndpoints.remove(endpointId)
                updatePeers()
            }
        }
    }

    // ------------------------------------------------------------------ connections

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            endpointNames[endpointId] = info.endpointName
            // Friends-only room: auto-accept every AirGap endpoint.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
            updateStatus("Joining ${info.endpointName}…")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            connectingEndpoints.remove(endpointId)
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpoints.add(endpointId)
                    addSystemMessage("${endpointNames[endpointId] ?: "Someone"} joined the room")
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                    addSystemMessage("${endpointNames[endpointId] ?: "A phone"} rejected the connection")
                else ->
                    addSystemMessage("Connection to ${endpointNames[endpointId] ?: "a phone"} failed")
            }
            updateStatus(if (running) "Live — advertising + discovering" else "Not started")
            updatePeers()
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints.remove(endpointId)
            addSystemMessage("${endpointNames[endpointId] ?: "Someone"} left the room")
            endpointNames.remove(endpointId)
            updatePeers()
        }
    }

    // ------------------------------------------------------------------ messages + mesh relay

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val raw = String(payload.asBytes() ?: return, StandardCharsets.UTF_8)
            if (raw.startsWith(MSG_PREFIX)) {
                handleProtocolMessage(endpointId, raw)
            } else {
                handleLegacyMessage(endpointId, raw)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Byte payloads are tiny chat lines; nothing to track in v2.
        }
    }

    /**
     * Handles a v2 `MSG|<id>|<ttl>|<nickname>|<text>` payload arriving from
     * [fromEndpointId]: dedup by id, display, then relay with ttl-1 to every
     * connected endpoint except the one it arrived from.
     */
    private fun handleProtocolMessage(fromEndpointId: String, raw: String) {
        // Split on only the first 4 separators so '|' inside the text survives.
        val parts = raw.split("|", limit = 5)
        if (parts.size < 5) {
            // Malformed MSG payload: fall back to showing it as a plain message.
            handleLegacyMessage(fromEndpointId, raw)
            return
        }
        val id = parts[1]
        val ttl = parts[2].toIntOrNull()
        if (id.isEmpty() || ttl == null) {
            handleLegacyMessage(fromEndpointId, raw)
            return
        }
        val sender = parts[3].ifEmpty { endpointNames[fromEndpointId] ?: "Unknown" }
        val text = parts[4]

        // Duplicate (already sent by me or already received/relayed): drop it.
        // This is also what stops my own message re-appearing if it loops back.
        if (!rememberMessageId(id)) return

        val viaRelay = ttl < INITIAL_TTL
        showChatMessage(
            ChatMessage(sender = sender, text = text, kind = ChatKind.THEIRS, viaRelay = viaRelay)
        )

        // Relay: only while ttl allows another hop, and never back to the sender endpoint.
        if (ttl > 1) {
            val forwarded = "$MSG_PREFIX$id|${ttl - 1}|$sender|$text"
            broadcastWire(forwarded, excludeEndpointId = fromEndpointId)
        }
    }

    /** Robustness fallback: a non-MSG payload is shown as a plain incoming message. */
    private fun handleLegacyMessage(fromEndpointId: String, raw: String) {
        val sep = raw.indexOf(LEGACY_SEPARATOR)
        val sender: String
        val text: String
        if (sep >= 0) {
            sender = raw.substring(0, sep)
            text = raw.substring(sep + LEGACY_SEPARATOR.length)
        } else {
            sender = endpointNames[fromEndpointId] ?: "Unknown"
            text = raw
        }
        showChatMessage(ChatMessage(sender = sender, text = text, kind = ChatKind.THEIRS))
    }

    private fun sendMessage() {
        val text = binding.messageInput.text.toString().trim()
        // Empty (or whitespace-only) messages are never sent.
        if (text.isEmpty()) return
        if (connectedEndpoints.isEmpty()) {
            Toast.makeText(this, "No phones connected yet", Toast.LENGTH_SHORT).show()
            return
        }
        val id = UUID.randomUUID().toString()
        // Mark my own id as seen BEFORE broadcasting, so a relayed copy that
        // loops back to me is dropped instead of re-displayed.
        rememberMessageId(id)
        val wire = "$MSG_PREFIX$id|$INITIAL_TTL|$myNickname|$text"
        broadcastWire(wire, excludeEndpointId = null)

        showChatMessage(ChatMessage(sender = getString(R.string.you_label), text = text, kind = ChatKind.MINE))
        binding.messageInput.text?.clear()
    }

    /** Sends one wire-format string to all connected endpoints except [excludeEndpointId]. */
    private fun broadcastWire(wire: String, excludeEndpointId: String?) {
        val targets = connectedEndpoints.filter { it != excludeEndpointId }
        if (targets.isEmpty()) return
        val payload = Payload.fromBytes(wire.toByteArray(StandardCharsets.UTF_8))
        connectionsClient.sendPayload(targets, payload)
    }

    /**
     * Records [id] in the bounded seen set.
     * @return true if the id was new, false if it had already been seen.
     */
    private fun rememberMessageId(id: String): Boolean {
        if (id in seenMessageIds) return false
        seenMessageIds.add(id)
        while (seenMessageIds.size > MAX_SEEN_IDS) {
            // firstOrNull keeps this safe even if the set is unexpectedly empty.
            val eldest = seenMessageIds.firstOrNull() ?: break
            seenMessageIds.remove(eldest)
        }
        return true
    }

    // ------------------------------------------------------------------ UI helpers

    private fun showChatMessage(message: ChatMessage) {
        chatAdapter.add(message)
        binding.messagesList.scrollToPosition(chatAdapter.itemCount - 1)
    }

    private fun addSystemMessage(text: String) {
        // Tactical system line format: ">> TEXT IN CAPS"
        val line = ">> " + text.removeSuffix(".").uppercase()
        showChatMessage(ChatMessage(sender = "", text = line, kind = ChatKind.SYSTEM))
    }

    private fun updateStatus(text: String) {
        binding.statusText.text = getString(R.string.status_format, text.uppercase())
    }

    private fun updatePeers() {
        val names = endpointNames.values.distinct().sorted()
        binding.peersText.text = if (names.isEmpty()) {
            getString(R.string.peers_format, 0, "NONE YET")
        } else {
            getString(R.string.peers_format, connectedEndpoints.size, names.joinToString(", "))
        }
    }

    // ------------------------------------------------------------------ permissions

    private fun requiredPermissions(): List<String> {
        val perms = mutableListOf<String>()
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                perms += Manifest.permission.BLUETOOTH_SCAN
                perms += Manifest.permission.BLUETOOTH_ADVERTISE
                perms += Manifest.permission.BLUETOOTH_CONNECT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms += Manifest.permission.NEARBY_WIFI_DEVICES
                } else {
                    // Android 12 / 12L still needs location for Wi-Fi discovery.
                    perms += Manifest.permission.ACCESS_FINE_LOCATION
                }
            }
            else -> {
                // Android 11 and below (down to minSdk 26).
                perms += Manifest.permission.ACCESS_FINE_LOCATION
            }
        }
        return perms
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val LEGACY_SEPARATOR = ": "

        /** v2 wire protocol prefix: `MSG|<id>|<ttl>|<nickname>|<text>`. */
        private const val MSG_PREFIX = "MSG|"

        /** Hops a fresh message may cross. A relay happens only while incoming ttl > 1. */
        private const val INITIAL_TTL = 3

        /** Cap on the dedup seen set; eldest ids are evicted beyond this. */
        private const val MAX_SEEN_IDS = 500

        /** Many-to-many: multiple phones can all connect into one shared room. */
        private val STRATEGY = Strategy.P2P_CLUSTER

        /** Only AirGap phones discover each other. The service id IS the app package. */
        private const val SERVICE_ID = "com.parth.airgap"
    }
}
