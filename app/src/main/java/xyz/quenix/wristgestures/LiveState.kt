package xyz.quenix.wristgestures

/**
 * In-process state shared between the calibration screen and the accessibility service.
 * Both run in the same process, so a plain object is enough.
 */
object LiveState {
    /**
     * `true` while the calibration screen is on top. The service then does not perform actions,
     * otherwise testing a flick would scroll or close the very screen you are testing on.
     */
    @Volatile
    var calibrationScreenVisible = false
}
