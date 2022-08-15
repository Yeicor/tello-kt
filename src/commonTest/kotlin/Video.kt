import com.soywiz.korio.file.VfsOpenMode
import com.soywiz.korio.file.fullName
import com.soywiz.korio.file.std.localVfs
import io.ktor.utils.io.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 *
 */
class VideoTests {

    @Test
    fun testProcessEcho() {
        runBlocking {
            val process = executeProcess(arrayOf("echo", "Hello World"))
            process.stdout.collect { println(String(it.data.copyOfRange(it.offset, it.length - it.offset))) }
        }
    }

    @Test
    fun testProcessCat() {
        runBlocking {
            val process = executeProcess(arrayOf("cat"))
            val sendData = "Hello world 2!".toByteArray()
            process.stdin(Buffer(sendData, 0, sendData.size), true)
            delay(500)
            launch { process.stdout.collect { println(String(it.data.copyOfRange(it.offset, it.length - it.offset))) } }
            delay(500)
            process.kill()
        }
    }

    @Test
    fun testProcessFfmpeg() {
        runBlocking {
            val process = executeProcess(arrayOf("ffmpeg", "-pix_fmts"))
            process.stdout.collect { println(String(it.data.copyOfRange(it.offset, it.length - it.offset))) }
        }
    }

    @Test
    fun testTelloVideo() {
        runBlocking {
            val tello = Tello() // Constructor should never fail (unless port is already in use)
            val telloVideo = Video()

            // Set up the video frame callback
//            val videoJob = launch {
//                telloVideo.getFrames960x720RGB888().collect {
//                    println("Frame received: ${it.copyOfRange(0, 16).asList()}...")
//                }
//            }

            val fileMeta = localVfs("/home/yeicor/").vfs.file("/home/yeicor/test.h264")
            val file = fileMeta.open(VfsOpenMode.CREATE_OR_TRUNCATE)
            println("Saving to ${fileMeta.fullName}")
            try {
                // This will fail (after a timeout) if the drone is not reachable
                // Make sure the computer is connected to the drone's WiFi.
                assertTrue { tello.enable() }

                for (i in 0..1000) {
                    // Wait for and read the state update of the drone
                    println(tello.readState())
                    // Wait for and read the next video frame
                    val h264Data = tello.readVideo()
                    file.write(h264Data.data.copy().readBytes())
                    telloVideo.feed(h264Data)
                    println(".")
                }
            } finally {
                file.close()
                tello.close()
//                videoJob.cancelAndJoin()
            }
        }
    }
}