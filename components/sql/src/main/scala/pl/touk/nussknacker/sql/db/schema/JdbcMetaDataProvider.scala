package pl.touk.nussknacker.sql.db.schema

import java.sql.Connection
import scala.util.Using

class JdbcMetaDataProvider(getConnection: () => Connection) extends DbMetaDataProvider {
  private def query(tableName: String) = s"SELECT * FROM $tableName"

  override def getDialectMetaData: DialectMetaData =
    Using.resource(getConnection()) { connection =>
      val metaData = connection.getMetaData
      DialectMetaData(metaData.getIdentifierQuoteString)
    }

  def getTableMetaData(tableName: String): TableMetaData = getQueryMetaDataImpl(query(tableName))

  def getSchemaDefinition(): SchemaDefinition =
    Using.resource(getConnection()) { connection =>
      val metaData        = connection.getMetaData
      val tables          = metaData.getTables(null, connection.getSchema, "%", Array("TABLE", "VIEW", "SYNONYM"))
      var results         = List[String]()
      val columnNameIndex = 3
      while (tables.next()) {
        val str: String = tables.getString(columnNameIndex)
        results = results :+ str
      }
      SchemaDefinition(results)
    }

  override def getQueryMetaData(query: String, resultStrategyName: String): TableMetaData =
    getQueryMetaDataImpl(query)

  private def getQueryMetaDataImpl(query: String): TableMetaData = {
    Using.resource(getConnection()) { connection =>
      Using.resource(connection.prepareStatement(query)) { statement =>
        TableMetaData( // For updates getMetaData return null, so TableDefinition is None
          Option(statement.getMetaData).map(TableDefinition(_)),
          DbParameterMetaData(statement.getParameterMetaData.getParameterCount)
        )
      }
    }
  }

}
