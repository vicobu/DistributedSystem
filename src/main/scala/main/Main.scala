/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */


package main


import java.io.{FileReader, BufferedReader}

import actors.node._
import actors.node.protocol.how.{Passive, Active}
import actors.node.protocol.when._
import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import net.message.ContentMessage
import transaction.{Transaction, Transactions, ReadWriteTransaction}
import transaction.operation.{CreateAttributeOperation, WriteOperation}
import scala.concurrent.duration._


object Main extends App {

  def startNodeSystems() = {
    ActorSystem("NodeA1System", ConfigFactory.load("nodeA1"))
    println("Started NodeA1System")

    ActorSystem("NodeA2System", ConfigFactory.load("nodeA2"))
    println("Started NodeA2System")

    ActorSystem("NodeA3System", ConfigFactory.load("nodeA3"))
    println("Started NodeA3System")

    ActorSystem("NodeB1System", ConfigFactory.load("nodeB1"))
    println("Started NodeB1System")

    ActorSystem("NodeB2System", ConfigFactory.load("nodeB2"))
    println("Started NodeB2System")

    ActorSystem("NodeC1System", ConfigFactory.load("nodeC1"))
    println("Started NodeC1System")

    ActorSystem("NodeC2System", ConfigFactory.load("nodeC2"))
    println("Started NodeC2System")
  }

  override def main(args: Array[String]) {

    startNodeSystems()

    val cdnMasterSystem = ActorSystem("CDNMasterSystem", ConfigFactory.load("cdnmaster"))

    // Create CDN Master
    val cdnMasterNode = cdnMasterSystem.actorOf(Props(new CDNMaster), name = "cdnMaster")

    // Create Nodes
    val nodeA1 = cdnMasterSystem.actorOf(Props(new CoreNode), name = "nodeA1")
    val nodeA2 = cdnMasterSystem.actorOf(Props(new CoreNode), name = "nodeA2")
    val nodeA3 = cdnMasterSystem.actorOf(Props(new CoreNode), name = "nodeA3")

    val nodeB1 = cdnMasterSystem.actorOf(Props(new CoreNode), name = "nodeB1")
    val nodeB2 = cdnMasterSystem.actorOf(Props(new CoreNode), name = "nodeB2")

    val nodeC1 = cdnMasterSystem.actorOf(Props(new CoreNode), name = "nodeC1")
    val nodeC2 = cdnMasterSystem.actorOf(Props(new CoreNode), name = "nodeC2")

    var ln = readLine()
    // Add Nodes to CDN Master
    cdnMasterNode ! AddNode(nodeA1, 0, AddNode.WriteMode)
    cdnMasterNode ! AddNode(nodeA2, 0, AddNode.WriteMode)
    cdnMasterNode ! AddNode(nodeA3, 0, AddNode.WriteMode)

    cdnMasterNode ! AddNode(nodeB1, 1, AddNode.ReadMode)
    cdnMasterNode ! AddNode(nodeB2, 1, AddNode.ReadMode)

    cdnMasterNode ! AddNode(nodeC1, 2, AddNode.ReadMode)
    cdnMasterNode ! AddNode(nodeC2, 2, AddNode.ReadMode)

    // Node Linkage
    cdnMasterNode ! RequestNodeConnection(
      (nodeA1, PortNode.portOf(new PortNode with Active with Eager), Nil),
      (nodeA2, PortNode.portOf(new PortNode with Active with Eager), Nil)
    )
    cdnMasterNode ! RequestNodeConnection(
      (nodeA1, PortNode.portOf(new PortNode with Active with Eager), Nil),
      (nodeA3, PortNode.portOf(new PortNode with Active with Eager), Nil)
    )
    cdnMasterNode ! RequestNodeConnection(
      (nodeA2, PortNode.portOf(new PortNode with Active with Eager), Nil),
      (nodeA3, PortNode.portOf(new PortNode with Active with Eager), Nil)
    )

    cdnMasterNode ! RequestNodeConnection(
      (nodeA2, PortNode.portOf(new PortNode with Passive with Lazy), List(("transBound", Condition.of(TransactionBoundary(10))))),
      (nodeB1, PortNode.portOf(new PortNode with Passive with Lazy), Nil)
    )

    cdnMasterNode ! RequestNodeConnection(
      (nodeA3, PortNode.portOf(new PortNode with Passive with Lazy), List(("transBound", Condition.of(TransactionBoundary(10))))),
      (nodeB2, PortNode.portOf(new PortNode with Passive with Lazy), Nil)
    )

    cdnMasterNode ! RequestNodeConnection(
      (nodeB2, PortNode.portOf(new PortNode with Passive with Lazy), List(("timeCond", Condition.of(TimeCondition(10.seconds))))),
      (nodeC1, PortNode.portOf(new PortNode with Passive with Lazy), Nil)
    )

    cdnMasterNode ! RequestNodeConnection(
      (nodeB2, PortNode.portOf(new PortNode with Passive with Lazy), List(("timeCond", Condition.of(TimeCondition(10.seconds))))),
      (nodeC2, PortNode.portOf(new PortNode with Passive with Lazy), Nil)
    )

    // Partition properties
    cdnMasterNode ! PartitionUpdate(0)

    var ok = true
    ln = readLine()

    while (ok) {
      ok = ln != null
      if (ok) {
        var transaction: Transaction = null
        var line: String = null
        try {
          val reader = new BufferedReader(new FileReader("./docs/transactions"))
          line = reader.readLine()
          while (line != null) {
            println("Read: " + line)
            transaction = Transactions.fromJSONObject(line)
            println("Size: " + transaction.operations.size)
            cdnMasterNode ! ContentMessage(transaction.id, transaction)
            val ln = readLine()
            line = reader.readLine()
          }
          reader.close()
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
    }
    //system.terminate()

  }

}
