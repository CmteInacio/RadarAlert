package com.radaralert.app.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

/**
 * Mantém a conexão Bluetooth Classic (SPP) com o ESP32, reconectando com
 * backoff exponencial quando a conexão cai.
 */
class BluetoothRepository(
    private val deviceAddress: String,
    private val scope: CoroutineScope
) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    @SuppressLint("MissingPermission")
    fun start() {
        scope.launch(Dispatchers.IO) {
            var backoffMillis = INITIAL_BACKOFF_MILLIS
            while (true) {
                if (connectOnce()) {
                    backoffMillis = INITIAL_BACKOFF_MILLIS
                } else {
                    delay(backoffMillis)
                    backoffMillis = (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectOnce(): Boolean = withContext(Dispatchers.IO) {
        try {
            val device = adapter?.getRemoteDevice(deviceAddress) ?: return@withContext false
            val newSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            newSocket.connect()
            socket = newSocket
            output = newSocket.outputStream
            _connected.value = true

            while (newSocket.isConnected) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                sendLine("PING")
            }
            true
        } catch (e: IOException) {
            false
        } catch (e: SecurityException) {
            false
        } finally {
            _connected.value = false
            closeQuietly()
        }
    }

    fun send(speedKmh: Int, maxSpeedKmh: Int?, distanceMeters: Int?, state: String, alertType: String) {
        val line = buildString {
            append("SPEED:").append(speedKmh)
            append(";MAXSPEED:").append(maxSpeedKmh ?: -1)
            append(";DIST:").append(distanceMeters ?: -1)
            append(";STATE:").append(state)
            append(";TYPE:").append(alertType)
        }
        sendLine(line)
    }

    private fun sendLine(line: String) {
        try {
            output?.write("$line\n".toByteArray())
        } catch (e: IOException) {
            _connected.value = false
        }
    }

    private fun closeQuietly() {
        try {
            socket?.close()
        } catch (e: IOException) {
            // conexão já estava fechada
        }
        socket = null
        output = null
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val INITIAL_BACKOFF_MILLIS = 2000L
        private const val MAX_BACKOFF_MILLIS = 16000L
        private const val HEARTBEAT_INTERVAL_MILLIS = 2000L
    }
}
