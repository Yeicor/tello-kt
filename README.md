# Tello-kt

A basic [DJI Tello](https://www.ryzerobotics.com/tello) drone controller
for [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html).

It is not a complete implementation of the Tello API, but it is enough to control the drone and receive video data. Feel
free to add more features from the SDK link below. Check out the [example](src/commonTest/kotlin/Tello.kt) to see how to
use it.

It uses the public command
API ([SDK 1.3.0.0](https://dl-cdn.ryzerobotics.com/downloads/tello/20180910/Tello%20SDK%20Documentation%20EN_1.3.pdf)),
so internal features like taking a 5MP photo or changing the video bitrate are not supported (check out
the [TelloPilots](https://tellopilots.com/wiki/development/) forum for more libraries that may support this).
