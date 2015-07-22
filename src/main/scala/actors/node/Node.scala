/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */


package actors.node

import actors.ComposableActor
import actors.node.protocol.how.HowAlgorithm
import actors.node.protocol.when.{NewCondition, WhenAlgorithm, Condition}
import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import net.message._
import transaction.Transaction

import scala.collection.mutable
import scala.concurrent.duration._

// Define the node specific messages
/**
 * sender() has received a transaction
 * @param transactionId the transaction id
 */
case class TransactionReceived(transactionId: Int) extends ACK

/**
 * sender() sends his vectorClock value
 * @param vectorClock the vectorClock value
 */
case class VectorClockUpdate(vectorClock: VectorClock.clockType) extends ControlMessage

/**
 * It says: "Connect a Node (node) to this node"
 * @param node  the node to connect to
 */
case class NewNode(node: ActorRef) extends DiscoveryAndLookupMessage

/**
 * Asks for the replication type of the node
 */
case class ReplicationType_?() extends DiscoveryAndLookupMessage

/**
 * Response to a replication type question
 * @param replicationType the replication type
 */
case class ReplicationType(replicationType: String) extends DiscoveryAndLookupMessage

/**
 * Message to create a new connection between node1 and node2 with the specified protocols
 * @param node1 Tuple of ActorRef, HowAlgorithm and WhenAlgorithm
 * @param node2 Tuple of ActorRef, HowAlgorithm and WhenAlgorithm
 */
case class RequestNodeConnection(node1: (ActorRef, () => PortNode, List[(String, () => Condition)]), node2: (ActorRef, () => PortNode, List[(String, () => Condition)])) extends ManageConnection

/**
 * Message te request a Node to create a new connection
 * @param id  the id of the connection
 * @param factoryMethod the factory method that returns the type of connection
 */
case class RequestNewConnection(override val id: Double, factoryMethod: () => PortNode, conditions: List[(String, () => Condition)]) extends NewConnection

/**
 * A Node sends this message to who has requested the new connection
 * @param id  the id of the new connection
 * @param port  the ActorRef that points to the newly created port
 */
case class ResponseConnectionCreated(override val id: Double, port: ActorRef) extends NewConnection


/**
 * A node of the distributed system
 * @param startLinkedToNodes a list containing the Nodes this Node will send transactions
 */
abstract class Node(val startLinkedToNodes: mutable.HashMap[ActorRef, String] = mutable.HashMap[ActorRef, String]())
  extends ComposableActor
  with WhenAlgorithm
  with HowAlgorithm {

  /**
   * The linked nodes list
   */
  protected var linkedToNodes = new mutable.HashMap[ActorRef, String]()
  linkedToNodes ++= startLinkedToNodes

  /**
   * The node vector clock
   */
  protected var vectorClock = VectorClock(VectorClock.Zero)

  receiveBuilder += {

    case contentMsg: ContentMessage =>
      vectorClock inc()
      vectorClock updateWith contentMsg.vectorClock

      contentMsg.content match {
        case transaction: Transaction =>
          doWhenTransactionReceived(transaction, sender())
      }

    case controlMsg: ControlMessage =>

      controlMsg match {
        case TransactionReceived(transactionId) => doWhenTransactionReceivedACK(transactionId)
        case VectorClockUpdate(vc) => vectorClock updateWith vc
        case dMsg: DiscoveryAndLookupMessage =>
          dMsg match {
            case ReplicationType_?() => sender ! ReplicationType(how)
            case NewNode(node) =>
              addNode(node)
          }
        case mngC: ManageConnection =>
          mngC match {
            case RequestNewConnection(id, factoryMethod, conditions) =>
              val portRef = context.actorOf(Props(factoryMethod()), id.toInt.toString)
              addNode(portRef)
              portRef ! NewNode(self)
              conditions.foreach {
                case (name, condition) =>
                  portRef ! NewCondition(name, condition)
              }
              sender ! ResponseConnectionCreated(id, portRef)
            case _ =>
          }
      }
  }

  /**
   * Adds a new node to the connected nodes.
   * @param node the node identity.
   */
  def addNode(node: ActorRef): Unit = {
    implicit val ec = context.dispatcher
    implicit val timeout = Timeout(1.seconds)
    val future = node ? ReplicationType_?()

    future.onSuccess {
      case result =>
        linkedToNodes += node -> result.asInstanceOf[ReplicationType].replicationType
        println(self.path + " added " + node.path + " as " + result.asInstanceOf[ReplicationType].replicationType)
    }
  }
}
