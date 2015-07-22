/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */

package actors

package object node {

  /**
   * Vector clock object.
   * Defines the clock type and translates values to the clockType
   */
  object VectorClock {

    type clockType = Double

    val Zero = value(0)

    def value(value: Double): clockType = value
  }

  /**
   * A class implementing an increasing vector clock
   * @param initialValue The initial value of the VectorClock
   */
  case class VectorClock(initialValue: VectorClock.clockType = VectorClock.Zero) {

    private var clockValue: VectorClock.clockType = initialValue

    /**
     * Gets the VectorClock value
     * @return  the VectorClock value
     */
    def value(): VectorClock.clockType = clockValue

    /**
     * Increases the VectorClock by 1
     */
    def inc(): Unit = clockValue = clockValue + VectorClock.value(1)

    /**
     * Updates the vector clock if vc is greater than the VectorClock
     * @param vc  a VectorClock value
     */
    def updateWith(vc: VectorClock.clockType): Unit = if (clockValue < vc ) clockValue = vc else clockValue

    /**
     * Sets the VectorClock value to vc
     * @param vc  a VectorClock value
     */
    def forceUpdateWith(vc: VectorClock.clockType) = clockValue = vc

  }

}
