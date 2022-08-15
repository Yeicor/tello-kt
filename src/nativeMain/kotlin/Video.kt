import kotlinx.cinterop.CPointer
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toLong
import kotlinx.coroutines.flow.flow
import platform.posix.*

/**
 * Platform-specific way of executing a process and returning its stdin and stdout
 */
actual fun executeProcess(cmd: Array<String>): Process {
    val command = cmd.joinToString(" ") // FIXME: Add quotes and escape some characters
    val fp: CPointer<FILE>? = popen(command, "r") // FIXME: Reading-only mode (need to implement this myself for read-write)

    /* Open the command for reading. */
    if (fp == NULL) {
        throw IllegalStateException("Failed to execute command: $command")
    }

    val buffer = ByteArray(4096)
    return Process({ buf, flush ->
        fwrite(buf.data.refTo(buf.offset), 1, buf.length.toULong(), fp)
        if (flush) {
            fflush(fp)
        }
    }, flow {
        // FIXME: Not working...
//        val read = recv(fp.toLong().toInt(), buffer.refTo(0), 1, 4096)
        val read = read(fp.toLong().toInt(), buffer.refTo(0), 4096)
//        val read = fread(buffer.refTo(0), 1, 4096, fp)
        if (read.toInt() > 0) {
            emit(Buffer(buffer, 0, read.toInt()))
        }
    }, {
        // FIXME: Kill process
        pclose(fp)
    })
}