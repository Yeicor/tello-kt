@file:Suppress("unused", "MemberVisibilityCanBePrivate")

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * TelloVideo connects to a Tello drone in command mode. It also optionally receives video frames from the drone.
 *
 * You should start by calling enable() to set the drone to command mode (and check the connection),
 * and start listening for state updates. Remember to close() when you're done.
 *
 * 
 */
class Tello(
    /**
     * The IP address of the Tello drone to send commands.
     */
    private val cmdAddr: SocketAddress = InetSocketAddress("192.168.10.1", 8889),
    /**
     * The IP address to bind to, to receive commands confirmations.
     */
    cmdBindAddr: SocketAddress = InetSocketAddress("0.0.0.0", 8889),
    /**
     * The IP address to bind to, to receive state updates.
     */
    private val stateBindAddr: SocketAddress = InetSocketAddress("0.0.0.0", 8890),
    /**
     * The port to bind to for receiving video packets.
     */
    private val videoBindAddr: SocketAddress = InetSocketAddress("0.0.0.0", 11111)
) {

    /**
     * The socket to send commands to the Tello drone.
     */
    private val cmdSocket: ConnectedDatagramSocket

    /**
     * The socket to receive state packets from the Tello drone.
     */
    private var stateSocket: BoundDatagramSocket? = null

    /**
     * The socket to receive video packets from the Tello drone.
     */
    private var videoSocket: BoundDatagramSocket? = null

    init {
        val selectorManager = SelectorManager(Dispatchers.Default)
        cmdSocket = aSocket(selectorManager).udp().connect(cmdAddr, cmdBindAddr)
    }

    /**
     * Sends a command to the Tello drone. Waits for the response (ok / error).
     */
    private suspend fun sendCmd(data: String, timeout: Duration): Boolean {
        cmdSocket.send(Datagram(ByteReadPacket(data.toByteArray()), cmdAddr))
        return withTimeout(timeout) {
            while (!cmdSocket.isClosed) {
                val datagram = cmdSocket.receive()
                if (datagram.address == cmdAddr) {
                    val packetText = datagram.packet.readText(2, 256)
                    println("Command $data received answer $packetText")
                    if (packetText.startsWith("ok")) {
                        return@withTimeout true
                    } else if (packetText.startsWith("error")) {
                        return@withTimeout false
                    }
                }
            }
            return@withTimeout false
        }
    }

    /**
     * Disconnects from the drone. REMEMBER TO LAND FIRST!
     */
    suspend fun close() {
        streamOff(timeout = 1.seconds)
        cmdSocket.close()
        stateSocket?.close()
        videoSocket?.close()
    }

    // ########################### BASIC COMMANDS ###########################

    /**
     * Enables the command mode for the drone. It can't be disabled unless the drone is rebooted.
     */
    suspend fun enable(timeout: Duration = 12.seconds): Boolean =
        sendCmd("command", timeout)

    /**
     * Emergency will stop the motors immediately without landing
     */
    suspend fun emergency(timeout: Duration = 12.seconds): Boolean =
        sendCmd("emergency", timeout)

    /**
     * Take off will make the drone take off.
     */
    suspend fun takeOff(timeout: Duration = 12.seconds): Boolean =
        sendCmd("takeoff", timeout)

    /**
     * Land will make the drone land.
     */
    suspend fun land(timeout: Duration = 12.seconds): Boolean =
        sendCmd("land", timeout)

    /**
     * StreamOn will start streaming video (use listenVideo to wait for a video frame).
     */
    suspend fun streamOn(timeout: Duration = 12.seconds): Boolean =
        sendCmd("streamon", timeout)

    /**
     * StreamOff will stop streaming video.
     */
    suspend fun streamOff(timeout: Duration = 12.seconds): Boolean =
        sendCmd("streamoff", timeout)

    /**
     * Speed will set the speed of the drone.
     */
    suspend fun speed(speed: Int, timeout: Duration = 12.seconds): Boolean =
        sendCmd("speed $speed", timeout)

    /**
     * Set the relative movement speed in each axis (@see speed). Range from -100 to +100. Z is UP.
     * yaw is the rotation speed in the same range.
     */
    suspend fun rc(x: Int, y: Int, z: Int, yaw: Int, timeout: Duration = 12.seconds): Boolean =
        sendCmd("rc $x $y $z $yaw", timeout)

    /**
     * Rotate the drone by the specified angle, measured in tenths of degrees. Range (+-)3600.
     */
    suspend fun rotate(angle: Int, timeout: Duration = 12.seconds): Boolean = if (angle > 0) {
        sendCmd("cw $angle", timeout)
    } else {
        sendCmd("ccw ${-angle}", timeout)
    }

    // Note: there are missing commands like flip and curve (easily implemented)

    // ########################### STATE MGMT ###########################

    /**
     * Waits and returns a state packet from the Tello drone.
     *
     * This returns important information like the remaining battery and should be queried continuously.
     */
    suspend fun readState(timeout: Duration = 12.seconds): CommandModeState {
        if (stateSocket == null) {
            val selectorManager = SelectorManager(Dispatchers.Default)
            stateSocket = aSocket(selectorManager).udp().bind(stateBindAddr)
        }

        return withTimeout(timeout) {
            while (true) {
                val receive = stateSocket!!.receive()
                if (receive.address == cmdAddr) {
                    return@withTimeout CommandModeState(receive.packet.readText())
                }
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Did not receive a video frame for too long")
        }
    }

    // ########################### VIDEO MGMT ###########################

    /**
     * Waits and returns a video packet from the Tello drone. The video is 720p@30FPS and is encoded in H264.
     *
     * If this is the first time it also enables the video streaming automatically
     */
    suspend fun readVideo(timeout: Duration = 3.seconds): TelloVideoPacket {
        if (videoSocket == null) {
            val selectorManager = SelectorManager(Dispatchers.Default)
            videoSocket = aSocket(selectorManager).udp().bind(videoBindAddr)
//            streamOn()
        }

        return withTimeout(timeout) {
            streamOn()
            while (true) {
                val receive = videoSocket!!.receive()
                return@withTimeout TelloVideoPacket(receive.packet)
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("Did not receive a video frame for too long")
        }
    }
}

/**
 * Holds a video packet received from the Tello drone.
 *
 * @see readVideoFrame
 */
data class TelloVideoPacket(val data: ByteReadPacket) {
    /**
     * This writes an H.264 NAL unit to the given byte array, and returns the written packet size.
     */
    fun readVideoFrame(to: ByteArray): Int {
        val length = data.remaining.toInt()/* - 2*/
        data.readFully(to, 0, length)
        return length
    }
}

/**
 * Holds the information provided by the tello when in command mode.
 *
 * 
 */
data class CommandModeState(
    var pitch: Int = 0,
    var roll: Int = 0,
    var yaw: Int = -45,
    var vgx: Int = 0,
    var vgy: Int = 0,
    var vgz: Int = 0,
    var templ: Int = 0,
    var temph: Int = 0,
    var tof: Int = 0,
    var h: Int = 0,
    var bat: Byte = 92,
    var baro: Float = 584.55f,
    var time: Float = 0.0f,
    var agx: Float = 0.0f,
    var agy: Float = 0.0f,
    var agz: Float = 0.0f,
) {
    /**
     * Parses the given string into the CommandModeState object.
     */
    constructor(data: String) : this() {
        for (pair in data.split(';')) {
            val kv = pair.split(':', limit = 2)
            when (kv[0]) {
                "pitch" -> pitch = kv[1].toInt()
                "roll" -> roll = kv[1].toInt()
                "yaw" -> yaw = kv[1].toInt()
                "vgx" -> vgx = kv[1].toInt()
                "vgy" -> vgy = kv[1].toInt()
                "vgz" -> vgz = kv[1].toInt()
                "templ" -> templ = kv[1].toInt()
                "temph" -> temph = kv[1].toInt()
                "tof" -> tof = kv[1].toInt()
                "h" -> h = kv[1].toInt()
                "bat" -> bat = kv[1].toByte()
                "baro" -> baro = kv[1].toFloat()
                "time" -> time = kv[1].toFloat()
                "agx" -> agx = kv[1].toFloat()
                "agy" -> agy = kv[1].toFloat()
                "agz" -> agz = kv[1].toFloat()
            }
        }
    }
}

fun Short.reverseBytes(): Short {
    val v0 = ((this.toInt() ushr 0) and 0xFF)
    val v1 = ((this.toInt() ushr 8) and 0xFF)
    return ((v1 and 0xFF) or (v0 shl 8)).toShort()
}