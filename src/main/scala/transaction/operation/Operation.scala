/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */


package transaction.operation

import scala.collection.mutable
import scala.collection.immutable.List
import org.json.JSONObject
import org.json.JSONArray

/**
 * An Operation
 */
trait Operation {
  val attribute: String
}

/**
 * An operation to create an attribute
 * @param attribute the attribute name
 */
sealed case class CreateAttributeOperation(override val attribute: String) extends Operation

/**
 *  A read operation
 * @param attribute  the variable to read from
 */
sealed case class ReadOperation(override val attribute: String) extends Operation

/**
 * A write oeration
 * @param attribute  the variable to transform
 * @param operation the operation
 * @param value the value of the operand
 */
sealed case class WriteOperation(override val attribute: String, operation: String, value: Int) extends Operation

object Operations {

  /**
   * Creates an Operation from a json object
   * @param jsonString  the json string representing an operation
   * @return  the operation
   */
  def fromJSONObject(jsonString: String): Operation = {
    val jsonObject = new JSONObject(jsonString)

    val operationType = jsonObject getInt "type"
    operationType match {
      case 0 => ReadOperation(jsonObject getString "from")
      case 1 => WriteOperation(jsonObject getString "to", jsonObject getString "op", jsonObject getInt "value")
      case 2 => CreateAttributeOperation(jsonObject getString "new")
    }
  }

  /**
   * Creates a list of Operations from a json array
   * @param jsonString  the json array of operation objects
   * @return  the list of operations
   */
  def fromJSONArray(jsonString: String): List[Operation] = {
    val operations = new mutable.MutableList[Operation]

    val jsonArray = new JSONArray(jsonString)

    for(i <- 0 until jsonArray.length()) {
      operations += Operations fromJSONObject(jsonArray get i toString())
    }

    operations.toList

  }

  /**
   * Thest method
   * @param args unused
   */
  def main(args: Array[String]) {

    val operations = Operations fromJSONArray "[{\"type\": 1, \"to\": \"a\", \"op\": \"eq\", \"value\": \"2\"}]"

    operations foreach {
      case ReadOperation(from) => println("ReadOperation -> read from " + from)
      case WriteOperation(to, operation, value) => println("WriteOperation -> write " + to + " " + operation + " " + value)
    }

  }

}