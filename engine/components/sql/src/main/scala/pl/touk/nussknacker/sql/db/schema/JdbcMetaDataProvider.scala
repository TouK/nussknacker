package pl.touk.nussknacker.sql.db.schema

import java.sql.Connection

class JdbcMetaDataProvider(getConnection: () => Connection) extends DbMetaDataProvider {

  def getDialectMetaData(): DialectMetaData = {
    val conn = getConnection()
    try {
      val metaData = conn.getMetaData
      DialectMetaData(metaData.getIdentifierQuoteString)
    } finally conn.close()
  }

  def getQueryMetaData(query: String): QueryMetaData = {
    val conn = getConnection()
    try {
      val statement = conn.prepareStatement(query)
      try {
        QueryMetaData(
          TableDefinition(statement.getMetaData),
          DbParameterMetaData(statement.getParameterMetaData.getParameterCount))
      } finally statement.close()
    } finally conn.close()
  }

  def getSchemaDefinition(): SchemaDefinition = {
    val connection = getConnection()
    try {
      val metaData = connection.getMetaData
      val tables = metaData.getTables(null, connection.getSchema, "%", Array("TABLE", "VIEW", "SYNONYM").map(_.toString))
      var results = List[String]()
      val columnNameIndex = 3
      while (tables.next()) {
        val str: String = tables.getString(columnNameIndex)
        results = results :+ str
      }
      SchemaDefinition(results)
    } finally connection.close()
  }
}
