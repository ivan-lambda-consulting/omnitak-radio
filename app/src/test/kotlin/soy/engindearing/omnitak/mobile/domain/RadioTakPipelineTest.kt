package soy.engindearing.omnitak.mobile.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import soy.engindearing.omnitak.mobile.data.ChatXml
import soy.engindearing.omnitak.mobile.data.ConnectionProtocol
import soy.engindearing.omnitak.mobile.data.SelfFix
import soy.engindearing.omnitak.mobile.data.ServerStoreApi
import soy.engindearing.omnitak.mobile.data.TAKServer
import soy.engindearing.omnitak.mobile.data.UserPrefs
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Network boundary: CoT/GeoChat ingestion and own PLI must not depend on a map screen. */
class RadioTakPipelineTest {
    private class MemoryServerStore(server: TAKServer) : ServerStoreApi {
        private val saved = MutableStateFlow(listOf(server))
        override val servers: StateFlow<List<TAKServer>> = saved
        override val activeServerId = MutableStateFlow<String?>(null)
        override suspend fun saveServers(list: List<TAKServer>) { saved.value = list }
        override suspend fun saveActiveServerId(id: String?) { activeServerId.value = id }
    }

    @Test fun inbound_contact_and_chat_survive_and_fresh_pli_reaches_a_tak_peer() = runBlocking {
        val listener = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val received = LinkedBlockingQueue<String>()
        val accepted = LinkedBlockingQueue<Socket>()
        val peer = thread(isDaemon = true) {
            try {
                val socket = listener.accept()
                accepted.offer(socket)
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val frame = StringBuilder()
                while (true) {
                    val next = reader.read()
                    if (next == -1) break
                    frame.append(next.toChar())
                    if (frame.endsWith("</event>")) {
                        received.offer(frame.toString())
                        frame.clear()
                    }
                }
            } catch (_: java.io.IOException) {
                // The test closes the listener/client at the end.
            }
        }
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val contacts = ContactStore()
        val chats = ChatStore()
        val server = TAKServer(
            id = "radio-test-server", name = "Radio Test", host = "127.0.0.1",
            port = listener.localPort, protocol = ConnectionProtocol.TCP.wire,
        )
        val manager = ServerManager(
            store = MemoryServerStore(server), contactStore = contacts,
            chatStore = chats, externalScope = scope,
        )
        val restored = SelfFix(
            lat = 47.6062, lon = -117.4194, altitudeM = 0.0, speedKmh = 0.0,
            accuracyM = Float.NaN, timeMs = 1_700_000_000_000L, restored = true,
        )
        val fix = MutableStateFlow<SelfFix?>(restored)
        val prefs = UserPrefs(selfUid = "ANDROID-radio-test", callsign = "RADIO-1")
        val broadcaster = SelfPositionBroadcaster(
            scope = scope,
            prefsFlow = MutableStateFlow(prefs),
            updatePrefs = { /* UID already exists */ },
            sendCoT = { manager.sendCoT(it) },
            locationFix = fix,
        )

        try {
            withTimeout(5_000) { manager.connectionState.first { it is ConnectionState.Connected } }
            val client = accepted.poll(5, TimeUnit.SECONDS)
                ?: error("the TAK peer did not accept the connection")
            // Send one position and one GeoChat frame over the actual socket.
            val contactXml = """<event version="2.0" uid="PEER-1" type="a-f-G" time="2026-01-01T00:00:00Z" start="2026-01-01T00:00:00Z" stale="2026-01-01T00:05:00Z" how="h-e"><point lat="52.5" lon="13.4" hae="0" ce="5" le="5"/><detail><contact callsign="PEER"/></detail></event>"""
            val chatXml = ChatXml.generateGeoChat(
                text = "check in", senderUid = "PEER-1", senderCallsign = "PEER",
                isGroup = false, recipientUid = prefs.selfUid,
                recipientCallsign = prefs.callsign, messageId = "radio-test-chat",
            ).xml
            client.getOutputStream().write((contactXml + chatXml).toByteArray(Charsets.UTF_8))
            client.getOutputStream().flush()
            withTimeout(5_000) { contacts.contacts.first { "PEER-1" in it } }
            withTimeout(5_000) {
                chats.messagesByConversation.first { conversations ->
                    conversations.values.flatten().any { it.text == "check in" }
                }
            }
            assertEquals("TAK: ${server.name}", contacts.contacts.value["PEER-1"]?.source?.label)

            broadcaster.broadcastOnce(prefs)
            assertEquals("restored fix cannot go on the wire", null, received.poll(250, TimeUnit.MILLISECONDS))
            broadcaster.start()

            fix.value = SelfFix(
                lat = 52.5, lon = 13.4, altitudeM = 34.0, speedKmh = 0.0,
                accuracyM = 5f, timeMs = System.currentTimeMillis(),
            )
            val pli = received.poll(5, TimeUnit.SECONDS)
            assertNotNull("fresh own PLI must reach the socket", pli)
            assertTrue(pli!!.contains("uid=\"${prefs.selfUid}\""))
            assertTrue(pli.contains("lat=\"52.5\""))
            assertTrue(pli.contains("lon=\"13.4\""))
            assertFalse(pli.contains("47.6062"))
        } finally {
            broadcaster.stop()
            manager.disconnect()
            scope.cancel()
            accepted.forEach { runCatching { it.close() } }
            listener.close()
            peer.join(1_000)
        }
    }
}
