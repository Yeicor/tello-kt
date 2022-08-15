import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 
 */
@Ignore
class TelloTests {
    @Test
    fun testDANGEROUSTakeOffMoveAndLand() {
        runBlocking {
            val tello = Tello() // Constructor should never fail (unless port is already in use)

            try {
                // This will fail (after a timeout) if the drone is not reachable
                // Make sure the computer is connected to the drone's WiFi.
                assertTrue { tello.enable() }
                assertTrue { tello.speed(20) } // Slow speed
                // Take off
                assertTrue { tello.takeOff() }
                // Wait for the drone to reach the takeoff altitude
                for (i in 0..50) { // 5s
                    println(tello.readState())
                    println(tello.readVideo())
                    delay(100)
                }
                // Move UP while rotating clockwise
                assertTrue { tello.rc(0, 0, 20, 20) }
                // Wait for the drone to reach the target position
                for (i in 0..10) { // 1s
                    println(tello.readState())
                    println(tello.readVideo())
                    delay(100)
                }
            } finally {
                // Stop moving and land
                assertTrue { tello.rc(0, 0, 0, 0) }
                tello.land()
                // Wait for the drone to land
                for (i in 0..50) { // 5s
                    println(tello.readState())
                    println(tello.readVideo())
                    delay(100)
                }
                tello.close()
            }
        }
    }

    @Test
    fun testReadOnly() {
        runBlocking {
            val tello = Tello() // Constructor should never fail (unless port is already in use)

            try {
                // This will fail (after a timeout) if the drone is not reachable
                // Make sure the computer is connected to the drone's WiFi.
                assertTrue { tello.enable() }

                for (i in 0..100) {
                    // Wait for and read the state update of the drone
                    println(tello.readState())
                    // Wait for and read the next video frame
                    println(tello.readVideo())
                }
            } finally {
                tello.close()
            }
        }
    }
}