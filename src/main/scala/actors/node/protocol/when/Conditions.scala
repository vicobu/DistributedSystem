/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */


package actors.node.protocol.when

import actors.ComposableActor
import akka.actor.{Props, ActorRef}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
 * Message that tells the ConditionHandler and the Node to create a ConditionActor
 * @param name  name of the ConditionActor
 * @param factoryMethod factory method that returns a ConditionActor
 */
case class NewCondition(name: String, factoryMethod: () => Condition)

/**
 * Condition results may extend from this trait
 */
trait ConditionResult

/**
 * A true condition result
 */
case class True() extends ConditionResult

/**
 * A false condition result
 */
case class False() extends ConditionResult

/**
 * Tells the node to check conditions to propagate transactions
 */
case class CheckCondition()

/**
 * Tells the node to propagate the buffered transactions
 */
case class SendBufferedTransactions()

/**
 * Actor representing a condition
 */
trait Condition extends ComposableActor {

  receiveBuilder += {
    case CheckCondition() =>
      context.parent ! checkCondition()
  }

  /**
   * Method to check for the veracity of the condition
   * @return
   */
  def checkCondition(): ConditionResult

}

/**
 * Condition companion object. Factory.
 */
object Condition {
  def of(condition: => Condition): () => Condition = () => condition
}

/**
 * A time condition
 * @param time  the time to schedule
 */
case class TimeCondition(time: FiniteDuration) extends Condition {

  case class Tick()

  var ticks: Double = 0

  implicit val ec = context.dispatcher

  context.system.scheduler.schedule(
    0.milliseconds,
    time,
    context.parent,
    True()
  )

  override def checkCondition(): ConditionResult = {
    False()
  }
}

/**
 * A number of transactions boundary condition.
 * @param number  the number of transactions that have to wait for sending a True() condition result
 */
case class TransactionBoundary(number: Integer) extends Condition {

  var numTransactions = 0

  override def checkCondition(): ConditionResult = {
    numTransactions += 1
    //println(self.path + ": From TransactionBoundary: {numTransactions: " + numTransactions + "}")
    if (numTransactions >= number) {
      //println(self.path + "True() sent")
      True()
    } else {
      False()
    }
  }

}

/**
 * Class to handle conditions. It talks between the conditions and the node.
 */
sealed case class ConditionHandler() extends ComposableActor {

  protected lazy val conditions = mutable.LinkedHashMap[String, ActorRef]()

  receiveBuilder += {
    case NewCondition(name, factoryMethod) =>
      conditions += name -> context.actorOf(Props(factoryMethod()), name)
    case CheckCondition() =>
      if (conditions.isEmpty) {
        context.parent ! SendBufferedTransactions()
      } else {
        conditions.foreach {
          case (_, condition) =>
            condition ! CheckCondition()
        }
      }
    case True() =>
      //println(self.path + "SendBufferedTransactions() sent")
      context.parent ! SendBufferedTransactions()
    case _ =>
  }
}