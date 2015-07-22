/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */

package actors.node.protocol.how

/**
 * HowAlgorithm companion object. Defines three actions with the transactions.
 * Active: execute the transaction and replicate the result.
 * Passive: Replicate the transaction and execute it.
 * Core: Save the transaction into the DataBase
 */
object HowAlgorithm {
  val Active = "Active"
  val Passive = "Passive"
  val Core = "Core"
}

/**
 * Trait to define The replication policy of a node.
 */
trait HowAlgorithm {
  protected val how: String
}

/**
 * Active policy.
 */
trait Active extends HowAlgorithm {
  val how = HowAlgorithm.Active
}

/**
 * Passive policy.
 */
trait Passive extends HowAlgorithm {
  val how = HowAlgorithm.Passive
}

/**
 * Core policy.
 */
trait Core extends HowAlgorithm {
  val how = HowAlgorithm.Core
}
