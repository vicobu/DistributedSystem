/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */

package actors.node

import actors.node.protocol.how.HowAlgorithm
import actors.node.protocol.when.WhenAlgorithm
import transaction.Transaction

/**
 * This node connects a core node with another core node.
 */
abstract class PortNode extends Node with WhenAlgorithm with HowAlgorithm {

  override def persistTransaction(transaction: Transaction): (Option[Transaction], Option[Transaction]) = (Some(transaction), Some(transaction))
}

object PortNode {
  def portOf(portNode: => PortNode): () => PortNode = () => portNode
}