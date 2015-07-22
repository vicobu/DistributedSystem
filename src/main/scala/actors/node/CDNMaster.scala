/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */


package actors.node

import actors._
import akka.actor.ActorRef
import net.message.{ContentMessage, ControlMessage, DiscoveryAndLookupMessage, Message}
import transaction.{ReadOnlyTransaction, ReadWriteTransaction, Transaction}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

/**
 * Message type for adding a new node to the onion system
 * @param node the node identity
 * @param idPart the partition where the node should be placed (logical place)
 * @param mode the mode of the node (write or read)
 */
case class AddNode(node: ActorRef, idPart: Int, mode: Int) extends DiscoveryAndLookupMessage

/**
 * AddNode companion object
 */
object AddNode {
  val WriteMode = 1
  val ReadMode = 0
}

case class PartitionUpdate(idPart: Int) extends DiscoveryAndLookupMessage

/**
 * This actor manages all new nodes and connections between them.
 */
class CDNMaster extends ComposableActor {


  /**
   * List of partitions
   */
  private val partitions = new mutable.HashMap[Int, Partition]()

  /**
   * List of pending connections between nodes.
   */
  private val pendingConnections = new mutable.HashMap[Double, ListBuffer[ActorRef]]()

  private var manageConnectionId: Double = 0

  /**
   * @return the next connection id to be used
   */
  private def nextManageConnectionId(): Double = {
    manageConnectionId += 1
    manageConnectionId
  }

  /**
   * Adds a partition to the list of partitions.
   * @param idPart the id of the partition
   * @return
   */
  private def addPartition(idPart: Int): Partition = {
    val p = new Partition
    partitions += idPart -> new Partition
    p
  }

  /**
   * The CDNMaster vector clock
   */
  private val vectorClock = VectorClock(VectorClock.Zero)

  receiveBuilder += {

    // If the actor receives a message...
    case msg: Message =>
      vectorClock inc()

      msg match {

        case contentMsg: ContentMessage =>
          vectorClock updateWith contentMsg.vectorClock

          contentMsg.content match {
            case transaction: Transaction =>
              transaction match {
                case ReadOnlyTransaction(id, operations, idPart) => sendReadOnlyTransaction(ReadOnlyTransaction(id, operations, idPart))
                case ReadWriteTransaction(id, operations, idPart) => sendReadWriteTransaction(ReadWriteTransaction(id, operations, idPart))
              }
          }

        case controlMsg: ControlMessage =>
          controlMsg match {
            //case TransactionReceived(transactionId) => doWhenTransactionReceivedACK(transactionId)
            case VectorClockUpdate(vc) => vectorClock updateWith vc
            case PartitionUpdate(idPart) => setWriteOperation(idPart)
            case dMsg: DiscoveryAndLookupMessage =>
              dMsg match {
                case AddNode(node, idPart, mode) =>
                  // Get the partition if exists, if not, create one
                  val partition = partitions.getOrElse(idPart,
                  {
                    val p = new Partition
                    partitions += idPart -> p
                    p
                  })

                  val readList = partition.readList
                  readList.contains(node) match {
                    case false => readList += node
                    case true => println("Error: Duplicated node in readList")
                  }
                  val writeList = partition.writeList
                  writeList.contains(node) match {
                    case false =>
                      mode match {
                        case AddNode.WriteMode => writeList += node
                        case AddNode.ReadMode => println("Node on read mode")
                      }
                    case true => println("Error: Duplicated node in writeList")
                  }
              }
            case RequestNodeConnection(node1, node2) =>
              val id = nextManageConnectionId()

              pendingConnections += id -> new ListBuffer[ActorRef]

              node1._1 ! RequestNewConnection(id, node1._2, node1._3)
              node2._1 ! RequestNewConnection(id, node2._2, node2._3)

            case ResponseConnectionCreated(id, port) =>
              pendingConnections.get(id) match {
                case None => println("Something goes wrong with the link creation")
                case Some(list) =>
                  list.+=(port)
                  if (list.size == 2) {
                    val port1 = list.head
                    val port2 = list.last
                    port1 ! NewNode(port2)
                    port2 ! NewNode(port1)
                  }
              }
            case TransactionReceived(id) =>
              println("Transaction " + id + ", job done!")
          }
      }
  }

  /**
   * Send a transaction to a read partition
   * @param transaction the transaction
   */
  private def sendReadOnlyTransaction(transaction: ReadOnlyTransaction): Unit = {
    val idPart = transaction.idPart
    partitions.get(idPart) match {
      case None => println("Error: no such partition " + idPart)
      case Some(partition) =>
        val keys = partition.readList
        val node = keys.toVector(Random.nextInt(keys.size))
        // Send the message
        node ! ContentMessage(VectorClock.Zero, transaction)
    }
  }

  /**
   * Sends a read/write transaction to a partition
   * @param transaction
   */
  private def sendReadWriteTransaction(transaction: ReadWriteTransaction): Unit = {
    val idPart = transaction.idPart
    partitions.get(idPart) match {
      case None => println("Error: no such partition " + idPart)
      case Some(partition) =>
        partition.writeOperation match {
          case true =>
            val keys = partition.writeList
            val node = keys.toVector(Random.nextInt(keys.size))
            // Send the message
            node ! ContentMessage(VectorClock.Zero, transaction)
            println("Transaction " + transaction.id + " sent to " + node.path.name + " from partition " + idPart)
          case false => println("Error: partition " + idPart + " not writable")
        }
    }
  }

  /**
   * Sets the partition with idPart to be writable. If the partition does not exist a message is printed in the screen
   * @param idPart the partition id
   */
  def setWriteOperation(idPart: Int): Unit = {
    partitions.get(idPart) match {
      case None => println("Error: no such partition " + idPart)
      case Some(partition) => {
        partition.writeOperation = true
//        println("Partition " + idPart + " is now writable")
      }
    }
  }

  /**
   * A partition containing read and write nodes. A partition can be writable or not. If a partition can be writable,
   * the client can write in any node of the partition.
   */
  private class Partition {
    lazy val readList = new mutable.HashSet[ActorRef]()
    lazy val writeList = new mutable.HashSet[ActorRef]()
    var writeOperation = false
  }

}