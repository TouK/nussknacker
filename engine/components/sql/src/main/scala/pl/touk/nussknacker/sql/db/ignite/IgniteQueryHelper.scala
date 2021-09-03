package pl.touk.nussknacker.sql.db.ignite

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.sql.db.ignite.TableCatalog.{ColumnMeta, TableMeta}

import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.collection.mutable.ArrayBuffer
import scala.util.Using

class IgniteQueryHelper(getConnection: () => Connection) extends LazyLogging {
  private val tablesInSchemaQuery =
    """
      |select t.TABLE_NAME, c.COLUMN_NAME, c.TYPE, c.AFFINITY_COLUMN
      |from SYS.TABLES t
      |join SYS.TABLE_COLUMNS c on t.TABLE_NAME = c.TABLE_NAME and t.SCHEMA_NAME = c.SCHEMA_NAME
      |where t.SCHEMA_NAME = ? and c.COLUMN_NAME not in ('_KEY', '_VAL')
      |""".stripMargin

  def fetchTableMeta(tableName: String): Option[TableMeta] = fetchTablesMeta.find(_.tableName == tableName)

  def fetchTablesMeta: List[TableMeta] = {
    Using.resource(getConnection()) { connection =>
      getIgniteQueryResults(connection = connection, query = tablesInSchemaQuery, setArgs = List(_.setString(1, connection.getSchema))) { r =>
        (r.getString("TABLE_NAME"), r.getString("COLUMN_NAME"), r.getString("TYPE"), r.getBoolean("AFFINITY_COLUMN"))
      }.groupBy(_._1)
        .map { case (tableName, entries) =>
          val columnTyping = entries.map { case (_, columnName, klassName, isAffinityColumn) =>
            ColumnMeta(columnName, Class.forName(klassName), isPartitionColumn = Some(isAffinityColumn))
          }
          TableMeta(tableName, columnTyping)
        }.toList
    }
  }

  private def getIgniteQueryResults[T](connection: Connection, query: String, setArgs: List[PreparedStatement => Unit] = Nil)(f: ResultSet => T): List[T] = {
    Using.resource(connection.prepareStatement(query)) { statement =>
      logger.debug(s"Executing query: $query")
      setArgs.foreach(setArg => setArg(statement))
      val resultSet = statement.executeQuery()
      val arr = ArrayBuffer.empty[T]
      while (resultSet.next()) {
        arr += f(resultSet)
      }
      arr.toList
    }
  }
}
