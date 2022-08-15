import io.ktor.utils.io.core.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlin.math.min

/**
 * A simple FFMPEG wrapper that can be used to convert the Tello raw video to the image frames
 *
 *
 */
class Video {
    private val process = executeProcess(
        arrayOf(
            "ffmpeg",
            "-f", "h264", "-framerate", "30", "-probesize", "32", "-i", "-",
            "-f", "rawvideo", "-pix_fmt", "rgb24", "-"
        )
    )

    suspend fun feed(h264Data: TelloVideoPacket) {
        val data = h264Data.data.readBytes()
//        println("Writing to ffmpeg ${data.size} bytes")
        process.stdin(Buffer(data, 2, data.size - 2), true)
    }

    @Suppress("unused")
    @OptIn(FlowPreview::class)
    suspend fun getFrames960x720RGB888(): Flow<ByteArray> {
        val fullFrameBuffer = ByteArray(960 * 720 * 3)
        var fullFrameBufferOffset = 0
        return process.stdout.flatMapConcat {
//            println("Read from ffmpeg ${it.length} bytes")
            flow {
                var itOffset = 0
                var copiedLength: Int
                do {
                    // Check how much we can copy
                    copiedLength = min(it.data.size - itOffset, fullFrameBuffer.size - fullFrameBufferOffset)
                    if (copiedLength <= 0) break

                    // Copy the data
                    it.data.copyInto(fullFrameBuffer, fullFrameBufferOffset, 0, copiedLength)
                    itOffset += copiedLength
                    fullFrameBufferOffset += copiedLength

                    // If we have a full frame, emit it
                    if (fullFrameBufferOffset == fullFrameBuffer.size) {
                        emit(fullFrameBuffer)
                        fullFrameBufferOffset = 0
                    }
                } while (copiedLength > 0)
            }
        }
    }
}

/**
 * Platform-specific way of executing a process and returning its stdin and stdout
 */
expect fun executeProcess(cmd: Array<String>): Process

/**
 * Generic process representation
 */
data class Process(
    /**
     * Write data to stdin and flush
     */
    val stdin: suspend (buf: Buffer, flush: Boolean) -> Unit,
    /**
     * Read data from stdout
     */
    val stdout: Flow<Buffer>,
    /**
     * Kill the process
     */
    val kill: suspend () -> Unit,
)

data class Buffer(val data: ByteArray, val offset: Int, val length: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Buffer

        if (!data.contentEquals(other.data)) return false
        if (offset != other.offset) return false
        if (length != other.length) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + offset
        result = 31 * result + length
        return result
    }
}