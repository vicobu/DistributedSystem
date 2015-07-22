/**
 * @author Victor Caballero (vicaba)
 * @author Xavier Domingo (xadobu)
 */


package transaction

import transaction.operation._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 *
 * @param attribute
 */
case class NoOperation(override val attribute: String = "noAttr") extends Operation

object DB {

  type Attribute = String

  type Value = Int

  /**
   * Test method
   */
  def main(args: Array[String]) {
    val db = new DB

    db.executeOperation(CreateAttributeOperation("a"))
    var row = db.executeOperation(ReadOperation("a"))
    println(row.value)
    row = db.executeOperation(WriteOperation("a", "+", 2))
    println(row.value)
    row = db.executeOperation(WriteOperation("a", "-", 3))
    println(row.value)

  }
}

/**
 * Class to simulate a DataBase
 */
class DB {

  /**
   * A row of the DataBase
   * @param attribute the attribute name
   * @param value the value of the attribute
   */
  case class Row(attribute: DB.Attribute, value: DB.Value)

  /**
   * Class to store the row logs
   * @param operation the operation performed
   * @param rowSnapshot a snapshot of the row when the operation is completed
   */
  case class LogRow(operation: Operation, rowSnapshot: Row)

  val attributes = new mutable.HashMap[DB.Attribute, ListBuffer[LogRow]]()

  val logHistory = new ListBuffer[LogRow]()

  /**
   * Executes the operation updating the attribute row and logging the operation
   * @param operation the operation to be performed
   * @throws java.lang.Exception if the attribute does not exist
   * @return the modified row (created or updated)
   */
  @throws(classOf[Exception])
  def executeOperation(operation: Operation): Row = {

    def logOperation(operation: Operation, row: Row): LogRow = {
      val logRow = LogRow(operation, row)
      attributes.get(operation.attribute) match {
        case None =>
          throw new IllegalAccessError
        case Some(log) =>
          logHistory.+=(logRow)
          log.+=(logRow)
          logRow
      }
    }

    def createAttribute(operation: Operation): Row = {
      val log = new ListBuffer[LogRow]()
      val row = Row(operation.attribute, 0)
      attributes += operation.attribute -> log
      logOperation(operation, row)
      row
    }

    @throws(classOf[Exception])
    def updateAttribute(): Row = {

      def modifyAttribute(row: Row, op: String, opValue: DB.Value): Row = {
        op match {
          case "=" =>
            Row(row.attribute, opValue)
          case "-" =>
            Row(row.attribute, row.value - opValue)
          case "+" =>
            Row(row.attribute, row.value + opValue)
        }
      }

      val opAttr = operation.attribute

      attributes.get(opAttr) match {
        case None => throw new IllegalAccessError
        case Some(attrLog) =>
          val row = attrLog.last.rowSnapshot
          operation match {
            case op: ReadOperation =>
              row
            case op: WriteOperation =>
              // Modify the attribute
              val newRow = modifyAttribute(row, op.operation, op.value)

              // Log the changes
              logOperation(operation, newRow)

              newRow
            case _ => row //TODO: Arreglar
          }
      }
    }

    operation match {
      case op: CreateAttributeOperation => createAttribute(op)
      case op: Operation => updateAttribute()

    }

  }

  /**
   * Executes the transaction against the DB
   * @param transaction the transaction to execute
   * @return  a compressed transaction
   */
  @throws(classOf[Exception])
  def executeTransaction(transaction: Transaction): Transaction = {
    var modifiedAttributes: List[DB.Attribute] = Nil
    var createdAttributes: List[CreateAttributeOperation] = Nil
    transaction.operations.foreach {
      operation =>
      executeOperation(operation)
        operation match {
          case WriteOperation(_, _, _) => modifiedAttributes = modifiedAttributes.+:(operation.attribute)
          case CreateAttributeOperation(attr) => createdAttributes = createdAttributes.+:(CreateAttributeOperation(attr))
          case _ =>
        }
    }

    modifiedAttributes = modifiedAttributes.distinct

    val operations = createdAttributes ++ modifiedAttributes.map {
      attr =>
        getAttribute(attr) match {
          case Some(row) => WriteOperation(row.attribute, "=", row.value)
          case None => NoOperation()
        }
    }.distinct diff List(NoOperation())

    println("Transaction: " + transaction.id + " operationsOfT: " + operations)

    operations.isEmpty match {
      case true => ReadOnlyTransaction(transaction.id, operations, transaction.idPart)
      case false => ReadWriteTransaction(transaction.id, operations, transaction.idPart)
    }

  }

  /**
   * Returns an Option holding the Global Log of the DataBase
   * @return an Option holding the Global Log of the DataBase
   */
  def getGlobalLog: Option[List[LogRow]] = logHistory.isEmpty match {
    case true => None
    case false => Some(logHistory.toList)
  }

  /**
   * Returns an Option holding the specified Attribute Log
   * @param attribute the name of the attribute
   * @return an Option holding the specified Attribute Log
   */
  def getAttributeLog(attribute: DB.Attribute): Option[List[LogRow]] = attributes.get(attribute) match {
    case None => None
    case Some(list) => Some(list.toList)
  }

  /**
   * Returns an Option holding the row matching the attribute name
   * @param attribute the name of the attribute
   * @return an Option holding the row matching the attribute name
   */
  def getAttribute(attribute: DB.Attribute): Option[Row] = {
    getAttributeLog(attribute) match {
      case None => None
      case Some(list) => Some(list.last.rowSnapshot)
    }
  }
}
