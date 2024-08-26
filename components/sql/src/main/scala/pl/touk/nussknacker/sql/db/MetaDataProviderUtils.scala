package pl.touk.nussknacker.sql.db

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.sql.db.schema.{DbParameterMetaData, TableDefinition, TableMetaData}

import java.sql.{Connection, PreparedStatement, ResultSet}
import scala.collection.mutable.ArrayBuffer
import scala.util.Using

object MetaDataProviderUtils extends LazyLogging {

  def createTableMetaData(
      tableName: String,
      tableDefinition: TableDefinition,
      getConnection: () => Connection
  ): TableMetaData = {
    Using.resource(getConnection()) { connection =>
      Using.resource(connection.prepareStatement(selectAllFromTableQuery(tableName))) { statement =>
        TableMetaData(
          Option(tableDefinition),
          DbParameterMetaData(statement.getParameterMetaData.getParameterCount)
        )
      }
    }
  }

  def getQueryResults[T](
      connection: Connection,
      query: String,
      setArgs: List[PreparedStatement => Unit] = Nil
  )(f: ResultSet => T): List[T] = {
    Using.resource(connection.prepareStatement(query)) { statement =>
      logger.debug(s"Executing query: $query")
      setArgs.foreach(setArg => setArg(statement))
      val resultSet = statement.executeQuery()
      val arr       = ArrayBuffer.empty[T]
      while (resultSet.next()) {
        arr += f(resultSet)
      }
      arr.toList
    }
  }

  private def selectAllFromTableQuery(tableName: String) = s"SELECT * FROM $tableName"
}
