package pl.touk.nussknacker.sql.db.ignite

import pl.touk.nussknacker.sql.db.schema._

import java.sql.Connection
import scala.util.Using

class IgniteMetaDataProvider(getConnection: () => Connection) extends JdbcMetaDataProvider(getConnection) {
  private def query(tableName: String) = s"SELECT * FROM $tableName"

  private val queryHelper = new IgniteQueryHelper(getConnection)

  override def getQueryMetaData(query: String): TableMetaData = throw new NotImplementedError("Generic query typing is not implemented for Ignite")

  override def getTableMetaData(tableName: String): TableMetaData = {
    val tableDefinition = queryHelper.fetchTablesMeta.getOrElse(tableName, throw new IllegalArgumentException("Table metadata not present"))
    Using.resource(getConnection()) { connection =>
      Using.resource(connection.prepareStatement(query(tableName))) { statement =>
        TableMetaData(
          tableDefinition,
          DbParameterMetaData(statement.getParameterMetaData.getParameterCount)
        )
      }
    }
  }

  override def getSchemaDefinition(): SchemaDefinition = SchemaDefinition(queryHelper.fetchTablesMeta.keys.toList)
}
