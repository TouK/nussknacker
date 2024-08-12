package pl.touk.nussknacker.sql.db.query

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.schema.TableDefinition

import java.sql.{PreparedStatement, ResultSet}
import java.util

trait QueryExecutor {

  type QueryResult

  def execute(statement: PreparedStatement): QueryResult

  protected def toTypedMap(tableDef: TableDefinition, resultSet: ResultSet): java.util.Map[String, Any] = {
    val fields = tableDef.columnDefs.map { columnDef =>
      // we could here use method resultSet.getObject(Int) and pass column number as argument
      // but in case of ignite db it is not certain which column index corresponds to which column.
      val value = resultSet.getObject(columnDef.name)
      columnDef.name -> columnDef.mapValue(value)
    }.toMap
    TypedMap(fields)
  }

}

class UpdateQueryExecutor extends QueryExecutor {

  override type QueryResult = UpdateResultStrategy.updateResultType

  def execute(statement: PreparedStatement): QueryResult = {
    statement.executeUpdate()
  }

}

class SingleResultQueryExecutor(tableDef: TableDefinition) extends QueryExecutor {

  override type QueryResult = java.util.Map[String, Any]

  def execute(statement: PreparedStatement): QueryResult = {
    val resultSet = statement.executeQuery()
    if (resultSet.next())
      toTypedMap(tableDef, resultSet)
    else
      null
  }

}

class ResultSetQueryExecutor(tableDef: TableDefinition) extends QueryExecutor {

  override type QueryResult = java.util.List[java.util.Map[String, Any]]

  override def execute(statement: PreparedStatement): QueryResult = {
    val resultSet = statement.executeQuery()
    val results   = new util.ArrayList[java.util.Map[String, Any]]()
    while (resultSet.next()) {
      results add toTypedMap(tableDef, resultSet)
    }
    results
  }

}
