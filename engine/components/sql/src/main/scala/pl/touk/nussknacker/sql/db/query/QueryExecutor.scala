package pl.touk.nussknacker.sql.db.query

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.sql.db.schema.TableDefinition

import java.sql.{PreparedStatement, ResultSet}
import scala.collection.mutable.ArrayBuffer

trait QueryExecutor {

  type QueryResult

  def execute(statement: PreparedStatement): QueryResult

  protected def toTypedMap(tableDef: TableDefinition, resultSet: ResultSet): TypedMap = {
    val fields = tableDef.columnDefs.map { columnDef =>
      columnDef.name -> resultSet.getObject(columnDef.no)
    }.toMap
    TypedMap(fields)
  }
}


class SingleResultQueryExecutor(tableDef: TableDefinition) extends QueryExecutor {

  override type QueryResult = TypedMap

  def execute(statement: PreparedStatement): QueryResult = {
    val resultSet = statement.executeQuery()
    if (resultSet.next())
      toTypedMap(tableDef, resultSet)
    else
      null
  }
}

class ResultSetQueryExecutor(tableDef: TableDefinition) extends QueryExecutor {
  import scala.collection.JavaConverters.bufferAsJavaListConverter

  override type QueryResult = java.util.List[TypedMap]

  override def execute(statement: PreparedStatement): QueryResult = {
    val resultSet = statement.executeQuery()
    val results = new ArrayBuffer[TypedMap]()
    while (resultSet.next()) {
      results += toTypedMap(tableDef, resultSet)
    }
    results.asJava
  }
}
