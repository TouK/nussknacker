package pl.touk.nussknacker.sql.db.schema

import java.sql.Connection
import scala.util.Using

class JdbcMetaDataProvider(getConnection: () => Connection) extends DbMetaDataProvider {
  private def query(tableName: String) = s"SELECT * FROM $tableName"

  def getDialectMetaData(): DialectMetaData =
    Using.resource(getConnection()) { connection  =>
      val metaData = connection.getMetaData
      DialectMetaData(metaData.getIdentifierQuoteString)
    }

  def getTableMetaData(tableName: String): TableMetaData = getQueryMetaData(query(tableName))

  def getSchemaDefinition(): SchemaDefinition =
    Using.resource(getConnection()) { connection =>
      val metaData = connection.getMetaData
      val tables = metaData.getTables(null, connection.getSchema, "%", Array("TABLE", "VIEW", "SYNONYM").map(_.toString))
      var results = List[String]()
      val columnNameIndex = 3
      while (tables.next()) {
        val str: String = tables.getString(columnNameIndex)
        results = results :+ str
      }
      SchemaDefinition(results)
    }

  override def getQueryMetaData(query: String): TableMetaData =
    Using.resource(getConnection()) { connection =>
      Using.resource(connection.prepareStatement(query)) { statement =>
        TableMetaData(
          TableDefinition(statement.getMetaData),
          DbParameterMetaData(statement.getParameterMetaData.getParameterCount))
      }
    }
}
