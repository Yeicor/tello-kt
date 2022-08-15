import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking

/**
 * Platform-specific way of executing a process and returning its stdin and stdout
 *
 * SAME IMPLEMENTATION AS JVM
 */
actual fun executeProcess(cmd: Array<String>): Process {
    val process = ProcessBuilder(cmd.toList()).start()
    return Process({ buffer, flush ->
        runBlocking(Dispatchers.IO) {
            process.outputStream.write(buffer.data, buffer.offset, buffer.length)
            if (flush) {
                process.outputStream.flush()
            }
        }
    }, flow {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = process.inputStream.read(buffer, 0, buffer.size)
            if (read < 0) break // EOF
            emit(Buffer(buffer, 0, read))
        }
    }.flowOn(Dispatchers.IO), {
        runBlocking(Dispatchers.IO) {
            process.inputStream.close()
            process.outputStream.close()
            process.destroy()
        }
    })
}