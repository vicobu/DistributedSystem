/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */

package actors.node

import java.io.{FileWriter, BufferedWriter, PrintWriter, File}

import actors.node.protocol.how.Core
import actors.node.protocol.when.Eager
import transaction.{DB, Transaction}

/**
 * This node is responsible for storing transactions.
 */
class CoreNode
  extends Node
  with Eager
  with Core
{

  protected val db = new DB()

  val dbFile: File = new File("./docs/nodeDB/" + self.path.name + ".txt")
  if(!dbFile.exists()) {
    dbFile.getParentFile.mkdirs()
    dbFile.createNewFile()
  }

  override def persistTransaction(transaction: Transaction): (Option[Transaction], Option[Transaction]) = {
    val writer = new PrintWriter(new BufferedWriter(new FileWriter(dbFile, true)))
    var reducedTransaction: Transaction = null
    try {
      reducedTransaction = db.executeTransaction(transaction)
      if(reducedTransaction.operations.isEmpty) {
        reducedTransaction = null
      } else {
        reducedTransaction.operations.foreach {
          operation =>
            println("I'M " + self.path.name + " " + operation.toString)
            writer.append(operation.toString + "\n")
        }
      }
    } catch {
      case e: IllegalAccessError => println("I'M " + self.path.name + " could not perform operation")
    }

    writer.close()

    (Option(transaction), Option(reducedTransaction))
  }

}
