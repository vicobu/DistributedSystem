/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */

package actors.node.protocol.when

import actors.node.protocol.how.HowAlgorithm
import actors.node.{Node, TransactionReceived}
import akka.actor.{ActorRef, Props}
import net.message.ContentMessage
import transaction.Transaction

import scala.collection.mutable

/**
 * WhenAlgorithm companion object.  Defines two replication policies.
 * Eager: Wait for others ACKs to return your own ACK.
 * Lazy: return the ACK just before receiving the transaction
 */
object WhenAlgorithm {
  val Eager = "Eager"
  val Lazy = "Lazy"
}

/**
 * Abstract trait for the implementation of input replication algorithms, attach it to a Node
 */
trait WhenAlgorithm {
  self: Node =>

  /**
   * The queue of Transactions that this Node needs to receive an acknowledgement
   */
  protected lazy val pendingQueue = mutable.LinkedHashMap[Int, PendingTransaction]()

  /**
   * A list of all the transactions this node has received.
   */
  protected lazy val stackedTransactions = mutable.ListBuffer[StackedTransaction]()

  /**
   * A condition handler to wait for conditions after propagating the transactions
   */
  protected val conditionHandler = context.actorOf(Props(ConditionHandler()), "ConditionHandler")

  /**
   * Implemented by concrete nodes to persist (or not a transaction)
   * @param transaction the transaction to persist
   * @return an Opton holding the full transaction and a possible reduced transaction
   */
  def persistTransaction(transaction: Transaction): (Option[Transaction], Option[Transaction])

  /**
   * Call when the node receives a transaction. Stop propagating if the transaction has been received.
   * @param transaction the transaction
   * @param sender  the sender of the transaction
   */
  def doWhenTransactionReceived(transaction: Transaction, sender: ActorRef) = {

    isAlreadyReceived(transaction.id) match {
      case true => sender ! TransactionReceived(transaction.id)
      case false => dealWithNewTransaction(transaction)
    }

  }

  protected def dealWithNewTransaction(transaction: Transaction) = {
    println(this.self.path + ": Transaction " + transaction.id + " received!")
    val transactions = persistTransaction(transaction)
    transactions._2 match {
      case Some(_) =>
        addPendingTransaction(transaction, sender())
        stackedTransactions += StackedTransaction(
          (transactions._1 orElse transactions._2).get,
          (transactions._2 orElse transactions._1).get
        )
        conditionHandler ! CheckCondition()
      case None => println(this.self.path + "None reduced Transaction received")
    }

  }

  /**
   * Call when an TransactionReceived message is received by a Node
   * @param transactionId the transaction id
   */
  def doWhenTransactionReceivedACK(transactionId: Int)

  protected def addPendingTransaction(transaction: Transaction, respondTo: ActorRef) = {
    pendingQueue += transaction.id -> PendingTransaction(transaction, respondTo, linkedToNodes - sender)
  }

  def propagateTransactions() = {
    stackedTransactions.toList.foreach {
      case (stackedTransaction) =>
        propagateTransaction(stackedTransaction.fullTransaction, stackedTransaction.reducedTransaction)
    }
    stackedTransactions.remove(0, stackedTransactions.size)
  }

  /**
   * Call when the Node has to propagate the transaction to the linked nodes
   */
  def propagateTransaction(fullTransaction: Transaction, reducedTransaction: Transaction) = {
    (linkedToNodes - sender()).foreach {
      case (node, howAlgorithm) =>
        howAlgorithm match {
          case HowAlgorithm.Active =>
            node ! ContentMessage(vectorClock.value(), fullTransaction)
          case HowAlgorithm.Passive =>
            node ! ContentMessage(vectorClock.value(), reducedTransaction)
          case HowAlgorithm.Core =>
            node ! ContentMessage(vectorClock.value(), fullTransaction)
        }
        println("I'm " + this.self.path + ". Propagated transaction " + fullTransaction.id + " to " + node.path)
    }
  }

  /**
   * Check if the transaction is already received.
   * @param id the transaction id
   * @return true or false
   */
  def isAlreadyReceived(id: Int): Boolean = pendingQueue.contains(id)

  receiveBuilder += {
    case SendBufferedTransactions() =>
      //println(this.self.path + "SendBufferedTransactions() received")
      propagateTransactions()
    case newCond: NewCondition =>
      conditionHandler ! newCond
  }
}

/**
 * Lazy replication algorithm
 */
trait Lazy extends WhenAlgorithm {
  self: Node =>


  override protected def dealWithNewTransaction(transaction: Transaction) = {
    super.dealWithNewTransaction(transaction)
    sender ! TransactionReceived(transaction.id)
  }

  def doWhenTransactionReceivedACK(transactionId: Int) = {}
}

/**
 * Eager replication algorithm
 */
trait Eager extends WhenAlgorithm {
  self: Node =>

  def doWhenTransactionReceivedACK(transactionId: Int) = {

    //println(this.self.path + ": ACK from " + sender().path + " received!")

    pendingQueue.get(transactionId) match {
      case Some(pendingTransaction) =>
        pendingTransaction.waitingFor.contains(sender()) match {
          case true =>
            pendingTransaction.waitingFor.remove(sender())
            if (pendingTransaction.waitingFor.isEmpty) {
              pendingTransaction.respondTo ! TransactionReceived(pendingTransaction.transaction.id)
            }
          case false =>
        }
      case None => println("No pending ACK to send with this transaction id(" + transactionId + ")")
    }
  }
}

/**
 * A class to manage pending transactions, it stores the Nodes the Node need to receive an Acknowledgement of TransactionReceived
 * @param transaction The pending transaction
 * @param respondTo Respond to this Node when all Nodes have received the transaction
 * @param waitingFor Waiting for this list of Nodes
 */
case class PendingTransaction(transaction: Transaction, respondTo: ActorRef, waitingFor: mutable.HashMap[ActorRef, String])

/**
 * A class tho store all the transactions received by this node.
 * @param fullTransaction the full transaction
 * @param reducedTransaction the reduced transaction (compressed reads and writes). E.g: create "a" && a+1 && a+1
 *                           is reduced to create "a" && a + 2
 */
case class StackedTransaction(fullTransaction: Transaction, reducedTransaction: Transaction)