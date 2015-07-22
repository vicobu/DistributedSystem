/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */

package transaction

import org.json.JSONObject

import scala.collection.immutable.List
import transaction.operation.{ReadOperation, Operations, Operation}
import net.message.MessageContent

/**
 * A transaction containing a list of operations
 */
sealed trait Transaction extends MessageContent {
  val id: Int
  val operations: List[Operation]
  val idPart: Int
}

/**
 * A transaction containing only read operations
 * @param id  the transaction id
 * @param operations  the list of operations
 * @param idPart  the id of the partition the transaction is aimed to write or read
 */
sealed case class ReadOnlyTransaction(override val id: Int, override val operations: List[Operation], override val idPart: Int) extends Transaction

/**
 * A transaction containing read and write operations
 * @param id  the transaction id
 * @param operations  the list of operations
 * @param idPart  the id of the partition the transaction is aimed to write or read
 */
sealed case class ReadWriteTransaction(override val id: Int, override val operations: List[Operation], override val idPart: Int) extends Transaction

object Transactions {

  /**
   * Creates a transaction from a json object
   * @param jsonString  the json string representing a transaction
   * @return  the transaction
   */
  def fromJSONObject(jsonString: String): Transaction = {
    val jsonObject = new JSONObject(jsonString)

    val id = jsonObject.getInt("id")
    val jsonOperations = jsonObject.getJSONArray("operations")
    val idPart = jsonObject.getInt("idPart")

    val operations = Operations.fromJSONArray(jsonOperations.toString)

    operations.forall {
      case ReadOperation(_) => true
      case _ => false
    } match {
      case true => ReadOnlyTransaction(id, operations, idPart)
      case false => ReadWriteTransaction(id, operations, idPart)
    }
  }

}