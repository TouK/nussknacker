package pl.touk.nussknacker.extensions.db.query

import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.extensions.db.schema.TableDefinition

import java.sql.{PreparedStatement, ResultSet}
import scala.collection.mutable.ArrayBuffer

class QueryExecutor(statement: PreparedStatement, tableDef: TableDefinition, strategy: QueryResultStrategy) {
  import scala.collection.JavaConverters.bufferAsJavaListConverter

  def execute(): Any = {
    val resultSet = statement.executeQuery()
    strategy match {
      case SingleResultStrategy => querySingleResult(resultSet).orNull
      case ResultSetStrategy => querySetResult(resultSet)
    }
  }

  private def querySingleResult(resultSet: ResultSet): Option[TypedMap] =
    if (resultSet.next())
      Some(toTypedMap(resultSet))
    else
      None

  private def querySetResult(resultSet: ResultSet): java.util.List[TypedMap] = {
    val results = new ArrayBuffer[TypedMap]()
    while (resultSet.next()) {
      results += toTypedMap(resultSet)
    }
    results.asJava
  }

  private def toTypedMap(resultSet: ResultSet): TypedMap = {
    val fields = tableDef.columnDefs.map { columnDef =>
      columnDef.name -> resultSet.getObject(columnDef.no)
    }.toMap
    TypedMap(fields)
  }
}
