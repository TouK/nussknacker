package pl.touk.nussknacker.sql.db.cassandra

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.sql.db.MetaDataProviderUtils
import pl.touk.nussknacker.sql.db.schema.{ColumnDefinition, TableDefinition}

import java.sql.Connection
import scala.util.Using

class CassandraQueryHelper(getConnection: () => Connection) extends LazyLogging {

  private val tableSchemaQuery =
    """
      |SELECT * FROM system_schema.columns
      |WHERE keyspace_name = ?
      |AND table_name = ?
      |""".stripMargin

  def fetchTableMeta(tableName: String): TableDefinition = {
    Using.resource(getConnection()) { connection =>
      val columnTypes = MetaDataProviderUtils.getQueryResults(
        connection = connection,
        query = tableSchemaQuery,
        setArgs = List(_.setString(1, connection.getSchema), _.setString(2, tableName))
      ) { r =>
        ColumnType(columnName = r.getString("column_name"), cqlType = r.getString("type"))
      }
      val columnDefinitions = columnTypes.map(columnType => {
        val javaClass = CassandraTypeToJavaTypeMapper.getJavaTypeFromCassandraType(columnType.cqlType)
        ColumnDefinition(name = columnType.columnName, typing = Typed.typedClass(javaClass))
      })
      TableDefinition(columnDefinitions)
    }
  }

  private case class ColumnType(
      columnName: String,
      // list of possible types is here https://cassandra.apache.org/doc/stable/cassandra/cql/types.html
      cqlType: String
  )

}
